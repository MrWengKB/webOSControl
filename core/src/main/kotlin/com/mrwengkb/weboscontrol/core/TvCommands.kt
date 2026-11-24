package com.mrwengkb.weboscontrol.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

data class VolumeStatus(val volume: Int?, val muted: Boolean)
data class AppInfo(val id: String, val title: String, val iconUrl: String?, val removable: Boolean = false)
data class InputInfo(val id: String, val label: String, val connected: Boolean)
data class TvInfo(val modelName: String?, val macAddress: String?)

/** What the TV reports about its on-screen text box while we are its remote keyboard. */
data class KeyboardState(val focused: Boolean, val password: Boolean = false, val contentType: String? = null)

/** Well-known SSAP endpoints. */
object Ssap {
    const val TURN_OFF = "ssap://system/turnOff"
    const val VOLUME_UP = "ssap://audio/volumeUp"
    const val VOLUME_DOWN = "ssap://audio/volumeDown"
    const val SET_VOLUME = "ssap://audio/setVolume"
    const val GET_VOLUME = "ssap://audio/getVolume"
    const val SET_MUTE = "ssap://audio/setMute"
    const val CHANNEL_UP = "ssap://tv/channelUp"
    const val CHANNEL_DOWN = "ssap://tv/channelDown"
    const val LIST_LAUNCH_POINTS = "ssap://com.webos.applicationManager/listLaunchPoints"
    const val LAUNCH = "ssap://system.launcher/launch"
    const val LIST_INPUTS = "ssap://tv/getExternalInputList"
    const val SWITCH_INPUT = "ssap://tv/switchInput"
    const val PLAY = "ssap://media.controls/play"
    const val PAUSE = "ssap://media.controls/pause"
    const val STOP = "ssap://media.controls/stop"
    const val REWIND = "ssap://media.controls/rewind"
    const val FAST_FORWARD = "ssap://media.controls/fastForward"
    const val TOAST = "ssap://system.notifications/createToast"
    const val INSERT_TEXT = "ssap://com.webos.service.ime/insertText"
    const val DELETE_CHARACTERS = "ssap://com.webos.service.ime/deleteCharacters"
    const val SEND_ENTER = "ssap://com.webos.service.ime/sendEnterKey"
    const val REGISTER_REMOTE_KEYBOARD = "ssap://com.webos.service.ime/registerRemoteKeyboard"
    const val SYSTEM_INFO = "ssap://system/getSystemInfo"
    const val CONNECTION_INFO = "ssap://com.webos.service.connectionmanager/getinfo"
    const val SW_INFO = "ssap://com.webos.service.update/getCurrentSWInformation"
}

/** Button names accepted by the pointer socket. */
object TvButton {
    const val UP = "UP"
    const val DOWN = "DOWN"
    const val LEFT = "LEFT"
    const val RIGHT = "RIGHT"
    const val ENTER = "ENTER"
    const val BACK = "BACK"
    const val HOME = "HOME"
    const val EXIT = "EXIT"
    const val MENU = "MENU"
    const val INFO = "INFO"
    const val GUIDE = "GUIDE"
    const val RED = "RED"
    const val GREEN = "GREEN"
    const val YELLOW = "YELLOW"
    const val BLUE = "BLUE"
    const val DASH = "DASH"
    fun number(n: Int): String = n.toString()
}

private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull

/** Reads both the old (`volume`, `muted`) and webOS 5+ (`volumeStatus`) reply shapes. */
fun parseVolume(payload: JsonObject): VolumeStatus {
    val status = payload["volumeStatus"] as? JsonObject
    return if (status != null) {
        VolumeStatus(status.int("volume"), status.bool("muteStatus") ?: status.bool("mute") ?: false)
    } else {
        VolumeStatus(payload.int("volume"), payload.bool("muted") ?: payload.bool("mute") ?: false)
    }
}

fun parseApps(payload: JsonObject): List<AppInfo> =
    (payload["launchPoints"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { lp ->
        val id = lp.string("id") ?: return@mapNotNull null
        AppInfo(id, lp.string("title") ?: id, lp.string("icon") ?: lp.string("largeIcon"), lp.bool("removable") ?: false)
    }.sortedBy { it.title.lowercase() }

fun parseInputs(payload: JsonObject): List<InputInfo> =
    (payload["devices"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { d ->
        val id = d.string("id") ?: return@mapNotNull null
        InputInfo(id, d.string("label") ?: id, d.bool("connected") ?: true)
    }

/** Parses a registerRemoteKeyboard update; null for the initial "subscribed" reply. */
fun parseKeyboard(payload: JsonObject): KeyboardState? {
    val widget = payload["currentWidget"] as? JsonObject ?: return null
    return KeyboardState(
        focused = widget.bool("focus") ?: false,
        password = widget.bool("hiddenText") ?: false,
        contentType = widget.string("contentType"),
    )
}

/**
 * How to turn the text the TV has into [new]: delete this many characters from the end, then
 * insert this text. Typing and backspacing at the end, the common case, cost one small step.
 */
fun textEdit(old: String, new: String): Pair<Int, String> {
    var common = 0
    val max = minOf(old.length, new.length)
    while (common < max && old[common] == new[common]) common++
    return (old.length - common) to new.substring(common)
}

/** Finds a MAC address in the connection manager reply, preferring the active interface. */
fun parseMac(connectionInfo: JsonObject?, swInfo: JsonObject?): String? {
    val wired = connectionInfo?.get("wiredInfo") as? JsonObject
    val wifi = connectionInfo?.get("wifiInfo") as? JsonObject
    val candidates = listOfNotNull(
        wired?.takeIf { it.string("state") == "connected" }?.string("macAddress"),
        wifi?.takeIf { it.string("state") == "connected" }?.string("macAddress"),
        wired?.string("macAddress"),
        wifi?.string("macAddress"),
        swInfo?.string("device_id"),
    )
    return candidates.firstOrNull { runCatching { WakeOnLan.parseMac(it) }.isSuccess }?.lowercase()
}

suspend fun WebOsClient.powerOff() {
    // The TV often drops the socket before replying, so a missing reply is not an error.
    runCatching { request(Ssap.TURN_OFF, timeoutMs = 2_000) }
}

suspend fun WebOsClient.volumeUp() = request(Ssap.VOLUME_UP)
suspend fun WebOsClient.volumeDown() = request(Ssap.VOLUME_DOWN)
suspend fun WebOsClient.setVolume(level: Int) =
    request(Ssap.SET_VOLUME, buildJsonObject { put("volume", level.coerceIn(0, 100)) })
suspend fun WebOsClient.setMute(mute: Boolean) = request(Ssap.SET_MUTE, buildJsonObject { put("mute", mute) })
suspend fun WebOsClient.getVolume(): VolumeStatus = parseVolume(request(Ssap.GET_VOLUME))
fun WebOsClient.volumeUpdates(): Flow<VolumeStatus> = subscribe(Ssap.GET_VOLUME).map(::parseVolume)

suspend fun WebOsClient.channelUp() = request(Ssap.CHANNEL_UP)
suspend fun WebOsClient.channelDown() = request(Ssap.CHANNEL_DOWN)

suspend fun WebOsClient.listApps(): List<AppInfo> = parseApps(request(Ssap.LIST_LAUNCH_POINTS))
suspend fun WebOsClient.launchApp(appId: String) = request(Ssap.LAUNCH, buildJsonObject { put("id", appId) })

suspend fun WebOsClient.listInputs(): List<InputInfo> = parseInputs(request(Ssap.LIST_INPUTS))
suspend fun WebOsClient.switchInput(inputId: String) =
    request(Ssap.SWITCH_INPUT, buildJsonObject { put("inputId", inputId) })

suspend fun WebOsClient.play() = request(Ssap.PLAY)
suspend fun WebOsClient.pause() = request(Ssap.PAUSE)
suspend fun WebOsClient.stop() = request(Ssap.STOP)
suspend fun WebOsClient.rewind() = request(Ssap.REWIND)
suspend fun WebOsClient.fastForward() = request(Ssap.FAST_FORWARD)

suspend fun WebOsClient.showToast(message: String) =
    request(Ssap.TOAST, buildJsonObject { put("message", message) })

suspend fun WebOsClient.insertText(text: String) =
    request(Ssap.INSERT_TEXT, buildJsonObject { put("text", text); put("replace", 0) })
suspend fun WebOsClient.deleteCharacters(count: Int) =
    request(Ssap.DELETE_CHARACTERS, buildJsonObject { put("count", count) })
suspend fun WebOsClient.sendEnter() = request(Ssap.SEND_ENTER)

/**
 * Registers this app as the TV's remote keyboard and reports when a text box gains or loses
 * focus. The TV only accepts insertText/deleteCharacters while this subscription is open.
 */
fun WebOsClient.keyboardUpdates(): Flow<KeyboardState> =
    subscribe(Ssap.REGISTER_REMOTE_KEYBOARD).mapNotNull(::parseKeyboard)

/** Model name and MAC address (the MAC is needed to turn the TV on with Wake-on-LAN). */
suspend fun WebOsClient.tvInfo(): TvInfo {
    val system = runCatching { request(Ssap.SYSTEM_INFO) }.getOrNull()
    val connection = runCatching { request(Ssap.CONNECTION_INFO) }.getOrNull()
    val sw = if (parseMac(connection, null) == null) runCatching { request(Ssap.SW_INFO) }.getOrNull() else null
    return TvInfo(system?.string("modelName"), parseMac(connection, sw))
}

// ---- YouTube search ------------------------------------------------------------------------
//
// The webOS YouTube app (youtube.leanback.v4, Cobalt) draws its own on-screen keyboard and
// ignores the TV's input method, so insertText reaches native webOS text boxes but never
// YouTube. Instead the app is relaunched with a search in its launch parameters.

const val YOUTUBE_APP_ID = "youtube.leanback.v4"
const val GET_FOREGROUND_APP = "ssap://com.webos.applicationManager/getForegroundAppInfo"
const val LIST_APPS = "ssap://com.webos.applicationManager/listApps"
private const val KEYWORD = "\$SEARCH_KEYWORD"

/** The app on screen, as its id (e.g. youtube.leanback.v4). */
fun WebOsClient.foregroundAppUpdates(): Flow<String> =
    subscribe(GET_FOREGROUND_APP).mapNotNull { it.string("appId")?.takeIf(String::isNotBlank) }

/**
 * Launch parameters that make YouTube search for [query]. Uses the search template the app
 * declares in `inAppSearchParams` (Cobalt on webOS 4+ declares `{"contentTarget":"q=$SEARCH_KEYWORD"}`),
 * and falls back to that same form when the TV doesn't report one.
 */
fun youtubeSearchParams(query: String, inAppSearchParams: kotlinx.serialization.json.JsonElement?): JsonObject {
    val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
    val template = when (inAppSearchParams) {
        is JsonObject -> inAppSearchParams
        is JsonPrimitive -> runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(inAppSearchParams.content) as? JsonObject
        }.getOrNull()
        else -> null
    }?.takeIf { it.toString().contains(KEYWORD) }
        ?: return buildJsonObject { put("contentTarget", "q=$encoded") }
    fun fill(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement = when (e) {
        is JsonObject -> JsonObject(e.mapValues { fill(it.value) })
        is JsonArray -> JsonArray(e.map(::fill))
        is JsonPrimitive -> if (e.isString && e.content.contains(KEYWORD)) JsonPrimitive(e.content.replace(KEYWORD, encoded)) else e
    }
    return fill(template) as JsonObject
}

/** Opens YouTube on the TV with a search for [query]. Returns the launch parameters used. */
suspend fun WebOsClient.searchYouTube(query: String): JsonObject {
    val apps = runCatching { request(LIST_APPS) }.getOrNull()
    val youtube = (apps?.get("apps") as? JsonArray)?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it.string("id") == YOUTUBE_APP_ID }
    val params = youtubeSearchParams(query, youtube?.get("inAppSearchParams"))
    request(Ssap.LAUNCH, buildJsonObject {
        put("id", YOUTUBE_APP_ID)
        put("params", params)
    })
    return params
}
