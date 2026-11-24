package com.mrwengkb.weboscontrol.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrwengkb.weboscontrol.RemoteViewModel
import com.mrwengkb.weboscontrol.core.AppInfo
import com.mrwengkb.weboscontrol.core.InputInfo
import com.mrwengkb.weboscontrol.core.KeyboardState
import com.mrwengkb.weboscontrol.core.TvButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppsSheet(
    apps: List<AppInfo>?,
    onPick: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirm by remember { mutableStateOf<AppInfo?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle("Apps")
        Text(
            "Tap to open an app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (apps == null) {
            Loading()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 520.dp),
            ) {
                items(apps, key = { it.id }) { app ->
                    Card(
                        Modifier.height(72.dp).combinedClickable(
                            onClick = { onPick(app) },
                            onLongClick = { confirm = app },
                            onLongClickLabel = "Uninstall",
                        ),
                    ) {
                        Box(Modifier.padding(8.dp).fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                            Text(
                                app.title,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
    confirm?.let { app ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Uninstall ${app.title}") },
            text = {
                Text(
                    "LG TVs don't allow phone apps to uninstall apps. To remove ${app.title}, use the LG remote: " +
                        "on the TV's app list, press and hold the app, then select the X.",
                )
            },
            confirmButton = { TextButton(onClick = { confirm = null }) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputsSheet(inputs: List<InputInfo>?, onPick: (InputInfo) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle("Inputs")
        when {
            inputs == null -> Loading()
            inputs.isEmpty() -> Text("The TV reported no external inputs.", Modifier.padding(24.dp))
            else -> LazyColumn(Modifier.navigationBarsPadding()) {
                items(inputs, key = { it.id }) { input ->
                    ListItem(
                        headlineContent = { Text(input.label) },
                        supportingContent = { Text(if (input.connected) "Connected" else "Nothing plugged in") },
                        modifier = Modifier.clickable { onPick(input) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSheet(vm: RemoteViewModel, keyboard: KeyboardState?, onDismiss: () -> Unit, youTube: Boolean = false) {
    // What we've typed into the TV's current text box. Every change is sent as you type.
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(keyboard?.focused) {
        // A newly focused text box on the TV starts empty from our point of view.
        if (keyboard?.focused == true) text = ""
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetTitle(if (youTube) "Search YouTube" else "Type on the TV")
            Text(
                if (youTube) "YouTube on LG TVs doesn't accept typed text from phones. Type your search here and " +
                    "press Search; YouTube opens its results." else when (keyboard?.focused) {
                    true -> "The TV's text box is ready. Letters appear on the TV as you type."
                    false -> "Select a text box on the TV (for example YouTube search) and start typing."
                    null -> "Select a text box on the TV, then type here."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    if (!youTube) vm.typed(text, new)
                    text = new
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                singleLine = true,
                label = { Text("Text") },
                visualTransformation = if (keyboard?.password == true) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (youTube) vm.searchYouTube(text) else vm.sendEnter() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (!youTube) vm.typed(text, ""); text = "" }, enabled = text.isNotEmpty()) { Text("Clear") }
                Spacer(Modifier.weight(1f))
                RemoteKey(Icons.AutoMirrored.Filled.Backspace, "Delete a character") {
                    if (youTube) {
                        text = text.dropLast(1)
                    } else if (text.isNotEmpty()) {
                        vm.typed(text, text.dropLast(1)); text = text.dropLast(1)
                    } else {
                        vm.backspace()
                    }
                }
                RemoteKey(Icons.AutoMirrored.Filled.KeyboardReturn, if (youTube) "Search" else "Enter") {
                    if (youTube) vm.searchYouTube(text) else vm.sendEnter()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberSheet(vm: RemoteViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle("Numbers")
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "-", "0", "OK")
        Column(
            Modifier.padding(horizontal = 32.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        FilledTonalButton(
                            onClick = {
                                vm.button(
                                    when (key) {
                                        "-" -> TvButton.DASH
                                        "OK" -> TvButton.ENTER
                                        else -> key
                                    },
                                )
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text(key, style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(32.dp))
    }
}
