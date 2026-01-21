@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.torpos.aadb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.torpos.aadb.service.AdbHostNotification
import com.torpos.aadb.state.AdbHostRepository
import com.torpos.aadb.state.ConnectionState
import com.torpos.aadb.state.isSelfHost
import com.torpos.aadb.state.parseHostPort

@Composable
fun AdbHostScreen() {
    val state by AdbHostRepository.state.collectAsState()
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var shellMode by rememberSaveable { mutableStateOf(false) }

    val pairingAddress = state.pairingAddress
    parseHostPort(pairingAddress)
    val pairingDone = state.connectionState == ConnectionState.Paired || state.connectionState == ConnectionState.Connected
    val pairingTitle = if (pairingDone && pairingAddress.isBlank()) "Pairing status" else "Pairing address"
    val pairingDisplay = if (pairingDone && pairingAddress.isBlank()) "Paired" else pairingAddress

    val connectAddress = state.connectAddress
    val connectTarget = parseHostPort(connectAddress)
    val connectHostOk = connectTarget?.first?.let { isSelfHost(it) } == true
    val connectValid = connectTarget != null && connectHostOk
    val connectTitle = if (state.connectionState == ConnectionState.Connected && connectAddress.isBlank()) {
        "Connection status"
    } else {
        "Device address"
    }
    val connectDisplay = if (state.connectionState == ConnectionState.Connected && connectAddress.isBlank()) {
        "Connected"
    } else {
        connectAddress
    }

    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        DecorativeOrbs()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "aadb", //aadb stands for android adb
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    )
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(Modifier.height(6.dp))
                }
                item {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Server") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Terminal") }
                        )
                    }
                }
                val lastError = state.lastError
                if (lastError != null) {
                    item {
                        ErrorCard(
                            message = lastError,
                            onDismiss = { AdbHostRepository.clearError() }
                        )
                    }
                }
                if (selectedTab == 0) {
                    item {
                        StatusCard(
                            serverRunning = state.serverRunning,
                            connectionState = state.connectionState,
                            onToggleServer = {
                                if (state.serverRunning) {
                                    AdbHostRepository.stopServer()
                                } else {
                                    AdbHostRepository.startServer()
                                }
                                AdbHostNotification.post(context)
                            }
                        )
                    }
                    item {
                        PairingCard(
                            pairingTitle = pairingTitle,
                            pairingAddress = pairingDisplay,
                            serverRunning = state.serverRunning,
                            pairingDone = pairingDone
                        )
                    }
                    item {
                        ConnectCard(
                            connectTitle = connectTitle,
                            connectAddress = connectDisplay,
                            connectEnabled = connectValid && state.serverRunning,
                            disconnectEnabled = state.serverRunning && state.connectionState == ConnectionState.Connected,
                            onConnect = {
                                AdbHostRepository.requestConnect(state.connectAddress)
                                AdbHostNotification.post(context)
                            },
                            onDisconnect = {
                                AdbHostRepository.requestDisconnect()
                                AdbHostNotification.post(context)
                            }
                        )
                    }

                } else {
                    item {
                        AdbInputCard(
                            command = state.commandInput,
                            onCommandChange = { AdbHostRepository.updateCommandInput(it) },
                            onRun = {
                                AdbHostRepository.runAdbCommand(state.commandInput, shellMode)
                                AdbHostNotification.post(context)
                            },
                            history = state.commandHistory,
                            serverRunning = state.serverRunning,
                            commandRunning = state.commandRunning,
                            lastCommand = state.lastCommand,
                            lastExitCode = state.lastCommandExitCode,
                            outputLines = state.lastCommandOutput,
                            onClearOutput = { AdbHostRepository.clearCommandOutput() },
                            onHistorySelect = { AdbHostRepository.updateCommandInput(it) },
                            shellMode = shellMode,
                            onToggleShellMode = { shellMode = !shellMode }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    serverRunning: Boolean,
    connectionState: ConnectionState,
    onToggleServer: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Server status",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Main adb host server. Must be running!", //makes sense
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (serverRunning) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            shape = CircleShape
                        )
                )
                Text(
                    text = if (serverRunning) "Server online" else "Server offline",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusPill(
                    text = if (serverRunning) "Server running" else "Server stopped",
                    background = if (serverRunning) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    textColor = if (serverRunning) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                StatusPill(
                    text = when (connectionState) {
                        ConnectionState.Idle -> "No device"
                        ConnectionState.Pairing -> "Pairing.."
                        ConnectionState.Paired -> "Paired"
                        ConnectionState.Connected -> "Connected"
                        ConnectionState.Error -> "Error. Check app"
                    },
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onToggleServer,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (serverRunning) "Stop server" else "Start server")
                }
            }
        }
    }
}

@Composable
private fun PairingCard(
    pairingTitle: String,
    pairingAddress: String,
    serverRunning: Boolean,
    pairingDone: Boolean
) {
    LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Pairing",
                style = MaterialTheme.typography.titleLarge
            )
            StepRow(
                step = "1",
                title = "Enable Wireless debugging",
                body = "Developer options > Turn on wireless debugging > Pair device with pairing code"
            )
            StepRow(
                step = "2",
                title = "Type the code into the notification",
                body = "Type the 6 digit code into the notification and press send"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AddressStatusRow(
                title = pairingTitle,
                value = pairingAddress,
                emptyHint = "Waiting for a pairing address...",
                showProgress = true
            )
            if (!serverRunning) {
                Text(
                    text = "Start the server to look for a pairing address.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (pairingAddress.isBlank() && !pairingDone) {
                Text(
                    text = "Waiting for Wireless debugging to broadcast a pairing address...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {



            }
        }
    }
}

@Composable
private fun ConnectCard(
    connectTitle: String,
    connectAddress: String,
    connectEnabled: Boolean,
    disconnectEnabled: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Connecting",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Auto detected wireless adb connection addresses", // this is a moutful
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AddressStatusRow(
                title = connectTitle,
                value = connectAddress,
                emptyHint = "Waiting for a connect address...",
                showProgress = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onConnect,
                    enabled = connectEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Connect")
                }
                FilledTonalButton(
                    onClick = onDisconnect,
                    enabled = disconnectEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}


@Composable
private fun AdbInputCard(
    command: String,
    onCommandChange: (String) -> Unit,
    onRun: () -> Unit,
    history: List<String>,
    serverRunning: Boolean,
    commandRunning: Boolean,
    lastCommand: String?,
    lastExitCode: Int?,
    outputLines: List<String>,
    onClearOutput: () -> Unit,
    onHistorySelect: (String) -> Unit,
    shellMode: Boolean,
    onToggleShellMode: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = if (shellMode) {
                    "Shell mode runs commands inside the device. Use adb root first if you need root."
                } else {
                    "Type an adb command and watch the output stream live."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val terminalBackground = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            val terminalText = MaterialTheme.colorScheme.onSurface
            val terminalMuted = MaterialTheme.colorScheme.onSurfaceVariant
            val terminalAccent = MaterialTheme.colorScheme.primary
            val terminalListState = rememberLazyListState()
            LaunchedEffect(outputLines.size, lastCommand) {
                if (outputLines.isNotEmpty()) {
                    terminalListState.animateScrollToItem(outputLines.size - 1)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (serverRunning) "Server running" else "Server stopped",
                    background = if (serverRunning) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    textColor = if (serverRunning) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                FilterChip(
                    selected = shellMode,
                    onClick = onToggleShellMode,
                    label = { Text(if (shellMode) "Shell mode" else "adb mode") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                )
            }
            if (!serverRunning) {
                Text(
                    text = "Server must be running to run commands",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = terminalBackground
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .padding(12.dp)
                ) {
                    if (lastCommand != null) {
                        Text(
                            text = "$ $lastCommand",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = terminalAccent
                        )
                    }
                    if (lastExitCode != null) {
                        Text(
                            text = "exit $lastExitCode",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (lastExitCode == 0) terminalAccent else MaterialTheme.colorScheme.error
                        )
                    }
                    if (outputLines.isEmpty()) {
                        Text(
                            text = "No output yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = terminalMuted
                        )
                    } else {
                        LazyColumn(
                            state = terminalListState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            items(outputLines) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = terminalText
                                )
                            }
                        }
                    }
                    if (commandRunning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = terminalAccent
                        )
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = terminalBackground
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (shellMode) "shell$" else "$",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        color = terminalAccent
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = command,
                        onValueChange = onCommandChange,
                        label = { Text(if (shellMode) "Shell command" else "Command") },
                        placeholder = { Text(if (shellMode) "whoami" else "adb devices") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRun,
                    enabled = command.isNotBlank() && serverRunning && !commandRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (commandRunning) "Running..." else "Run")
                }
                FilledTonalButton(
                    onClick = onClearOutput,
                    enabled = outputLines.isNotEmpty() || lastExitCode != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear output")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "Recent commands",
                style = MaterialTheme.typography.titleMedium
            )
            if (history.isEmpty()) {
                Text(
                    text = "Nothing yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.take(4).forEach { entry ->
                        AssistChip(
                            onClick = { onHistorySelect(entry) },
                            label = { Text(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Action needed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun StepRow(step: String, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddressStatusRow(
    title: String,
    value: String,
    emptyHint: String,
    showProgress: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        if (value.isBlank()) {
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showProgress) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, background: Color, textColor: Color) {
    Surface(
        color = background,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun DecorativeOrbs() {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .padding(top = 40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.BottomStart)
                .padding(bottom = 80.dp)
                .background(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    shape = CircleShape
                )
        )
    }
}
