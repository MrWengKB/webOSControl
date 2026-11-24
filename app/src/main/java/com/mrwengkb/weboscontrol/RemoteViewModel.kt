package com.mrwengkb.weboscontrol

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrwengkb.weboscontrol.core.AppInfo
import com.mrwengkb.weboscontrol.core.foregroundAppUpdates
import com.mrwengkb.weboscontrol.core.searchYouTube
import com.mrwengkb.weboscontrol.core.InputInfo
import com.mrwengkb.weboscontrol.core.KeyboardState
import com.mrwengkb.weboscontrol.core.keyboardUpdates
import com.mrwengkb.weboscontrol.core.textEdit
import com.mrwengkb.weboscontrol.core.PointerSocket
import com.mrwengkb.weboscontrol.core.SsdpDiscovery
import com.mrwengkb.weboscontrol.core.TvDevice
import com.mrwengkb.weboscontrol.core.VolumeStatus
import com.mrwengkb.weboscontrol.core.WakeOnLan
import com.mrwengkb.weboscontrol.core.WebOsClient
import com.mrwengkb.weboscontrol.core.channelDown
import com.mrwengkb.weboscontrol.core.channelUp
import com.mrwengkb.weboscontrol.core.deleteCharacters
import com.mrwengkb.weboscontrol.core.fastForward
import com.mrwengkb.weboscontrol.core.insertText
import com.mrwengkb.weboscontrol.core.launchApp
import com.mrwengkb.weboscontrol.core.listApps
import com.mrwengkb.weboscontrol.core.listInputs
import com.mrwengkb.weboscontrol.core.pause
import com.mrwengkb.weboscontrol.core.play
import com.mrwengkb.weboscontrol.core.powerOff
import com.mrwengkb.weboscontrol.core.rewind
import com.mrwengkb.weboscontrol.core.sendEnter
import com.mrwengkb.weboscontrol.core.setMute
import com.mrwengkb.weboscontrol.core.stop
import com.mrwengkb.weboscontrol.core.switchInput
import com.mrwengkb.weboscontrol.core.tvInfo
import com.mrwengkb.weboscontrol.core.volumeDown
import com.mrwengkb.weboscontrol.core.volumeUp
import com.mrwengkb.weboscontrol.core.volumeUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetAddress

enum class Screen { Devices, Remote }

data class UiState(
    val screen: Screen = Screen.Devices,
    val discovering: Boolean = false,
    val discovered: List<TvDevice> = emptyList(),
    val saved: List<SavedTv> = emptyList(),
    val current: SavedTv? = null,
    val connection: WebOsClient.State = WebOsClient.State.Disconnected,
    val volume: VolumeStatus? = null,
    val apps: List<AppInfo>? = null,
    val inputs: List<InputInfo>? = null,
    val waking: Boolean = false,
    /** Non-null while we are registered as the TV's keyboard; focused when a text box is active. */
    val keyboard: KeyboardState? = null,
    /** A newer release to offer, shown as a dialog. */
    val update: UpdateInfo? = null,
    val checkingUpdate: Boolean = false,
    val message: String? = null,
    /** The app on the TV's screen, e.g. youtube.leanback.v4. */
    val foregroundApp: String? = null,
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TvStore(app)
    private val updates = UpdateChecker(app)
    private val _ui = MutableStateFlow(UiState(saved = store.all()))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var client: WebOsClient? = null
    private var connectJob: Job? = null
    private var sessionJobs = mutableListOf<Job>()
    private var pointer: PointerSocket? = null
    private val pointerMutex = Mutex()

    /** Text edits go to the TV one at a time, in order, so fast typing doesn't get scrambled. */
    private val typing = Channel<suspend (WebOsClient) -> Unit>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (edit in typing) {
                val c = client?.takeIf { it.isConnected } ?: continue
                try {
                    edit(c)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showMessage(e.message ?: "Typing failed")
                }
            }
        }
        // Debug builds are test builds; only release builds look for updates on their own.
        if (!BuildConfig.DEBUG) checkForUpdates(manual = false)
        val last = store.last()
        if (last != null) {
            _ui.update { it.copy(screen = Screen.Remote, current = last) }
        } else {
            discover()
        }
    }

    // ---- Lifecycle -------------------------------------------------------------------------

    /** Called when the app comes to the foreground: reconnect to the current TV if needed. */
    fun onForeground() {
        val tv = _ui.value.current ?: return
        val state = _ui.value.connection
        if (connectJob?.isActive != true && state !is WebOsClient.State.Connected) connect(tv)
    }

    /** Called when the app goes to the background: drop the connection to save battery. */
    fun onBackground() {
        disconnect()
    }

    override fun onCleared() {
        disconnect()
    }

    // ---- Devices ---------------------------------------------------------------------------

    fun showDevices() {
        _ui.update { it.copy(screen = Screen.Devices, saved = store.all()) }
        if (_ui.value.discovered.isEmpty()) discover()
    }

    fun showRemote() {
        if (_ui.value.current != null) _ui.update { it.copy(screen = Screen.Remote) }
    }

    fun discover() {
        if (_ui.value.discovering) return
        _ui.update { it.copy(discovering = true) }
        viewModelScope.launch {
            val wifi = getApplication<Application>().applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wifi.createMulticastLock("webos-discovery").apply { setReferenceCounted(false) }
            val found = try {
                lock.acquire()
                SsdpDiscovery.discover()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Discovery failed: ${e.message}")
                emptyList()
            } finally {
                runCatching { lock.release() }
            }
            _ui.update { it.copy(discovering = false, discovered = found) }
        }
    }

    fun select(device: TvDevice) {
        val existing = store.all().firstOrNull { it.host == device.host }
        val tv = existing?.copy(name = device.name) ?: SavedTv(host = device.host, name = device.name)
        select(tv)
    }

    fun select(tv: SavedTv) {
        store.save(tv)
        _ui.update { it.copy(screen = Screen.Remote, current = tv, saved = store.all(), apps = null, inputs = null) }
        connect(tv)
    }

    fun addManually(host: String) {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return
        select(TvDevice(host = trimmed, name = "LG TV ($trimmed)"))
    }

    fun forget(tv: SavedTv) {
        store.remove(tv.host)
        if (_ui.value.current?.host == tv.host) {
            disconnect()
            _ui.update { it.copy(current = null) }
        }
        _ui.update { it.copy(saved = store.all()) }
    }

    // ---- Connection ------------------------------------------------------------------------

    fun reconnect() {
        _ui.value.current?.let(::connect)
    }

    private fun connect(tv: SavedTv) {
        disconnect()
        val c = WebOsClient(tv.host)
        client = c
        sessionJobs += viewModelScope.launch {
            c.state.collect { s -> if (client === c) _ui.update { it.copy(connection = s) } }
        }
        connectJob = viewModelScope.launch {
            try {
                val key = c.connect(tv.clientKey)
                var updated = tv.copy(clientKey = key)
                saveCurrent(updated)
                onConnected(c)
                val info = runCatching { c.tvInfo() }.getOrNull()
                if (info?.macAddress != null && info.macAddress != updated.macAddress) {
                    updated = updated.copy(macAddress = info.macAddress)
                    saveCurrent(updated)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // State flow already shows the failure; nothing else to do.
            }
        }
    }

    private fun saveCurrent(tv: SavedTv) {
        store.save(tv)
        _ui.update { it.copy(current = tv, saved = store.all()) }
    }

    private fun onConnected(c: WebOsClient) {
        sessionJobs += viewModelScope.launch {
            runCatching { c.volumeUpdates().collect { v -> _ui.update { it.copy(volume = v) } } }
        }
        sessionJobs += viewModelScope.launch {
            try {
                c.keyboardUpdates().collect { k -> _ui.update { it.copy(keyboard = k) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Older TVs may not support the remote keyboard; typing then falls back to plain insertText.
            }
        }
        sessionJobs += viewModelScope.launch {
            runCatching { c.foregroundAppUpdates().collect { app -> _ui.update { it.copy(foregroundApp = app) } } }
        }
        viewModelScope.launch { ensurePointer() }
    }

    /** YouTube ignores typed text, so the keyboard sends it a search instead. */
    fun searchYouTube(query: String) {
        if (query.isBlank()) return
        command { it.searchYouTube(query) }
    }

    private fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
        pointer?.close()
        pointer = null
        client?.close()
        client = null
        _ui.update { it.copy(connection = WebOsClient.State.Disconnected, volume = null, keyboard = null, foregroundApp = null) }
    }

    private suspend fun ensurePointer(): PointerSocket? = pointerMutex.withLock {
        val c = client?.takeIf { it.isConnected } ?: return null
        pointer?.takeIf { it.isOpen }?.let { return it }
        pointer = runCatching { c.openPointerSocket() }.getOrNull()
        pointer
    }

    // ---- Commands --------------------------------------------------------------------------

    private fun command(block: suspend (WebOsClient) -> Unit) {
        val c = client
        if (c == null || !c.isConnected) {
            showMessage("Not connected to the TV")
            return
        }
        viewModelScope.launch {
            try {
                block(c)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(e.message ?: "Command failed")
            }
        }
    }

    fun button(name: String) {
        viewModelScope.launch {
            val p = ensurePointer()
            if (p == null || !p.button(name)) {
                // The socket may have dropped; reopen once and retry.
                pointerMutex.withLock { pointer = null }
                if (ensurePointer()?.button(name) != true) showMessage("Not connected to the TV")
            }
        }
    }

    fun move(dx: Int, dy: Int) {
        viewModelScope.launch { ensurePointer()?.move(dx, dy) }
    }

    fun click() {
        viewModelScope.launch { ensurePointer()?.click() }
    }

    fun scroll(dy: Int) {
        viewModelScope.launch { ensurePointer()?.scroll(0, dy) }
    }

    fun volumeUp() = command { it.volumeUp() }
    fun volumeDown() = command { it.volumeDown() }
    fun toggleMute() = command { it.setMute(!(_ui.value.volume?.muted ?: false)) }
    fun channelUp() = command { it.channelUp() }
    fun channelDown() = command { it.channelDown() }
    fun play() = command { it.play() }
    fun pause() = command { it.pause() }
    fun stop() = command { it.stop() }
    fun rewind() = command { it.rewind() }
    fun fastForward() = command { it.fastForward() }
    fun launchApp(app: AppInfo) = command { it.launchApp(app.id) }
    fun switchInput(input: InputInfo) = command { it.switchInput(input.id) }
    /** Mirrors an edit of the phone's text field onto the TV's focused text box. */
    fun typed(old: String, new: String) {
        val (delete, insert) = textEdit(old, new)
        if (delete == 0 && insert.isEmpty()) return
        typing.trySend { c ->
            if (delete > 0) c.deleteCharacters(delete)
            if (insert.isNotEmpty()) c.insertText(insert)
        }
    }

    fun sendEnter() {
        typing.trySend { it.sendEnter() }
    }

    fun backspace() {
        typing.trySend { it.deleteCharacters(1) }
    }

    fun loadApps() = command { c -> _ui.update { it.copy(apps = c.listApps()) } }
    fun loadInputs() = command { c -> _ui.update { it.copy(inputs = c.listInputs()) } }

    /** Turns the TV off when connected, otherwise tries to wake it with Wake-on-LAN. */
    fun power() {
        val c = client
        if (c != null && c.isConnected) {
            viewModelScope.launch {
                c.powerOff()
                disconnect()
            }
            return
        }
        val tv = _ui.value.current ?: return
        val mac = tv.macAddress
        if (mac == null) {
            showMessage("Connect once while the TV is on so the app can learn its MAC address for power-on.")
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(waking = true) }
            try {
                val subnetBroadcast = withContext(Dispatchers.IO) {
                    runCatching {
                        val parts = InetAddress.getByName(tv.host).address
                        listOf(InetAddress.getByAddress(byteArrayOf(parts[0], parts[1], parts[2], 0xFF.toByte())))
                    }.getOrDefault(emptyList())
                }
                WakeOnLan.wake(mac, subnetBroadcast)
                // Give the TV time to boot its network stack, then keep trying to connect.
                repeat(8) {
                    delay(2_500)
                    if (_ui.value.current?.host != tv.host) return@launch
                    connect(_ui.value.current!!)
                    connectJob?.join()
                    if (client?.isConnected == true) return@launch
                }
                showMessage("The TV did not wake up. Check that \"Turn on via Wi-Fi\" is enabled on the TV.")
            } finally {
                _ui.update { it.copy(waking = false) }
            }
        }
    }

    // ---- Updates ---------------------------------------------------------------------------

    /**
     * Looks for a newer release. The automatic check on launch stays silent when there is
     * nothing new or the network fails, and skips a version the user ignored; a manual check
     * reports the result and offers ignored versions again.
     */
    fun checkForUpdates(manual: Boolean = true) {
        if (_ui.value.checkingUpdate) return
        _ui.update { it.copy(checkingUpdate = true) }
        viewModelScope.launch {
            try {
                val found = updates.latestNewerThan(BuildConfig.VERSION_NAME)
                when {
                    found == null -> if (manual) showMessage("You have the latest version (${BuildConfig.VERSION_NAME}).")
                    !manual && found.version == updates.ignoredVersion -> Unit
                    else -> _ui.update { it.copy(update = found) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) showMessage("Couldn't check for updates: ${e.message}")
            } finally {
                _ui.update { it.copy(checkingUpdate = false) }
            }
        }
    }

    /** "Ignore": don't offer this version again automatically. */
    fun ignoreUpdate() {
        _ui.value.update?.let { updates.ignoredVersion = it.version }
        _ui.update { it.copy(update = null) }
    }

    fun dismissUpdate() = _ui.update { it.copy(update = null) }

    fun showMessage(text: String) = _ui.update { it.copy(message = text) }
    fun messageShown() = _ui.update { it.copy(message = null) }
}
