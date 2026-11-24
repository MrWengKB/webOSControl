package com.mrwengkb.weboscontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrwengkb.weboscontrol.RemoteViewModel
import com.mrwengkb.weboscontrol.UiState
import com.mrwengkb.weboscontrol.core.TvButton
import com.mrwengkb.weboscontrol.core.WebOsClient
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Sheet { Apps, Inputs, Keyboard, Numbers }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(state: UiState, vm: RemoteViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var touchpad by rememberSaveable { mutableStateOf(false) }

    // Open the keyboard by itself when a text box gets focus on the TV (e.g. YouTube search).
    LaunchedEffect(state.keyboard?.focused) {
        if (state.keyboard?.focused == true && sheet == null) sheet = Sheet.Keyboard
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.current?.name ?: "No TV", maxLines = 1)
                        Text(
                            statusText(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = vm::showDevices) { Icon(Icons.Default.Tv, contentDescription = "Choose TV") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ConnectionBanner(state, vm)

            // Power and shortcuts
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val haptics = LocalHapticFeedback.current
                FilledIconButton(
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.power() },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = PowerRed, contentColor = Color.White),
                    modifier = Modifier.size(56.dp),
                ) {
                    if (state.waking) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power")
                }
                Spacer(Modifier.weight(1f))
                RemoteKey(Icons.AutoMirrored.Filled.Input, "Inputs") { vm.loadInputs(); sheet = Sheet.Inputs }
                Spacer(Modifier.width(10.dp))
                RemoteKey(Icons.Default.Apps, "Apps") { vm.loadApps(); sheet = Sheet.Apps }
                Spacer(Modifier.width(10.dp))
                RemoteKey(Icons.Default.Keyboard, "Keyboard") { sheet = Sheet.Keyboard }
                Spacer(Modifier.width(10.dp))
                RemoteKey(Icons.Default.Dialpad, "Number pad") { sheet = Sheet.Numbers }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !touchpad, onClick = { touchpad = false }, label = { Text("Arrows") })
                FilterChip(selected = touchpad, onClick = { touchpad = true }, label = { Text("Touchpad") })
            }

            if (touchpad) Touchpad(vm) else DPad(vm)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RemoteKey(Icons.AutoMirrored.Filled.ArrowBack, "Back") { vm.button(TvButton.BACK) }
                RemoteKey(Icons.Default.Home, "Home") { vm.button(TvButton.HOME) }
                RemoteKey(Icons.Default.Settings, "Settings") { vm.button(TvButton.MENU) }
                RemoteKey(Icons.Default.Info, "Info") { vm.button(TvButton.INFO) }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Rocker(
                    label = state.volume?.volume?.let { "VOL $it" } ?: "VOL",
                    upIcon = Icons.AutoMirrored.Filled.VolumeUp, upLabel = "Volume up", onUp = vm::volumeUp,
                    downIcon = Icons.AutoMirrored.Filled.VolumeDown, downLabel = "Volume down", onDown = vm::volumeDown,
                )
                RemoteKey(
                    icon = Icons.AutoMirrored.Filled.VolumeOff,
                    label = if (state.volume?.muted == true) "Unmute" else "Mute",
                    highlighted = state.volume?.muted == true,
                    onClick = vm::toggleMute,
                )
                Rocker(
                    label = "CH",
                    upIcon = Icons.Default.KeyboardArrowUp, upLabel = "Channel up", onUp = vm::channelUp,
                    downIcon = Icons.Default.KeyboardArrowDown, downLabel = "Channel down", onDown = vm::channelDown,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RemoteKey(Icons.Default.FastRewind, "Rewind", onClick = vm::rewind)
                RemoteKey(Icons.Default.PlayArrow, "Play", onClick = vm::play)
                RemoteKey(Icons.Default.Pause, "Pause", onClick = vm::pause)
                RemoteKey(Icons.Default.Stop, "Stop", onClick = vm::stop)
                RemoteKey(Icons.Default.FastForward, "Fast forward", onClick = vm::fastForward)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ColorKey(Color(0xFFE5484D), "Red") { vm.button(TvButton.RED) }
                ColorKey(Color(0xFF30A46C), "Green") { vm.button(TvButton.GREEN) }
                ColorKey(Color(0xFFFFC53D), "Yellow") { vm.button(TvButton.YELLOW) }
                ColorKey(Color(0xFF3E63DD), "Blue") { vm.button(TvButton.BLUE) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    when (sheet) {
        Sheet.Apps -> AppsSheet(
            state.apps,
            onPick = { vm.launchApp(it); sheet = null },
            onDismiss = { sheet = null },
        )
        Sheet.Inputs -> InputsSheet(state.inputs, onPick = { vm.switchInput(it); sheet = null }, onDismiss = { sheet = null })
        Sheet.Keyboard -> KeyboardSheet(
            vm, state.keyboard, onDismiss = { sheet = null },
            youTube = state.foregroundApp == com.mrwengkb.weboscontrol.core.YOUTUBE_APP_ID,
        )
        Sheet.Numbers -> NumberSheet(vm, onDismiss = { sheet = null })
        null -> Unit
    }
}

private fun statusText(state: UiState): String = when {
    state.waking -> "Waking the TV…"
    else -> when (val c = state.connection) {
        WebOsClient.State.Connected -> "Connected · ${state.current?.host}"
        WebOsClient.State.Connecting -> "Connecting…"
        WebOsClient.State.AwaitingPairing -> "Waiting for you to accept on the TV"
        WebOsClient.State.Disconnected -> "Not connected"
        is WebOsClient.State.Failed -> "Offline"
    }
}

@Composable
private fun ConnectionBanner(state: UiState, vm: RemoteViewModel) {
    val connection = state.connection
    if (connection == WebOsClient.State.Connected || state.waking) return
    val (text, showRetry) = when (connection) {
        WebOsClient.State.AwaitingPairing ->
            "Your TV is asking whether to allow this phone. Select Accept with the TV remote." to false
        WebOsClient.State.Connecting -> "Connecting to the TV…" to false
        is WebOsClient.State.Failed ->
            "${connection.message}\nIf the TV is off, press Power to wake it." to true
        else -> "Not connected." to true
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!showRetry) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (showRetry) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = vm::reconnect) { Text("Connect") }
            }
        }
    }
}

@Composable
fun RemoteKey(
    icon: ImageVector,
    label: String,
    size: Dp = 52.dp,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    FilledTonalIconButton(
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        modifier = Modifier.size(size),
        colors = if (highlighted) IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) else IconButtonDefaults.filledTonalIconButtonColors(),
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun ColorKey(color: Color, label: String, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(width = 56.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(color)
            .clickable(onClickLabel = label) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    )
}

@Composable
private fun Rocker(
    label: String,
    upIcon: ImageVector, upLabel: String, onUp: () -> Unit,
    downIcon: ImageVector, downLabel: String, onDown: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            Modifier.padding(6.dp).width(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RemoteKey(upIcon, upLabel, onClick = onUp)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            RemoteKey(downIcon, downLabel, onClick = onDown)
        }
    }
}

@Composable
private fun DPad(vm: RemoteViewModel) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(240.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        @Composable
        fun Arrow(icon: ImageVector, label: String, button: String, alignment: Alignment) {
            Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = alignment) {
                IconButton(
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.button(button) },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(icon, contentDescription = label, modifier = Modifier.size(40.dp))
                }
            }
        }
        Arrow(Icons.Default.KeyboardArrowUp, "Up", TvButton.UP, Alignment.TopCenter)
        Arrow(Icons.Default.KeyboardArrowDown, "Down", TvButton.DOWN, Alignment.BottomCenter)
        Arrow(Icons.Default.KeyboardArrowLeft, "Left", TvButton.LEFT, Alignment.CenterStart)
        Arrow(Icons.Default.KeyboardArrowRight, "Right", TvButton.RIGHT, Alignment.CenterEnd)
        FilledIconButton(
            onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.button(TvButton.ENTER) },
            modifier = Modifier.size(96.dp),
        ) {
            Text("OK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** Drag to move the TV's pointer, tap to click, drag the strip on the right to scroll. */
@Composable
private fun Touchpad(vm: RemoteViewModel) {
    Row(Modifier.fillMaxWidth().height(280.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var remX by remember { mutableStateOf(0f) }
        var remY by remember { mutableStateOf(0f) }
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) { detectTapGestures(onTap = { vm.click() }) }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        remX += drag.x * POINTER_SPEED
                        remY += drag.y * POINTER_SPEED
                        val dx = remX.roundToInt()
                        val dy = remY.roundToInt()
                        if (dx != 0 || dy != 0) {
                            remX -= dx
                            remY -= dy
                            vm.move(dx, dy)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Drag to move · tap to click",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        var remScroll by remember { mutableStateOf(0f) }
        Box(
            Modifier
                .width(44.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        remScroll += dy * SCROLL_SPEED
                        if (abs(remScroll) >= 1f) {
                            val step = remScroll.roundToInt()
                            remScroll -= step
                            vm.scroll(step)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("⇅", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private const val POINTER_SPEED = 0.8f
private const val SCROLL_SPEED = 0.15f
