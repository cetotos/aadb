package com.torpos.aadb.state

enum class ConnectionState {
    Idle,
    Pairing,
    Paired,
    Connected,
    Error,
}

data class AdbHostState(
    val serverRunning: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val pairingAddress: String = "",
    val pairingCode: String = "",
    val connectAddress: String = "",
    val commandInput: String = "",
    val commandHistory: List<String> = emptyList(),
    val lastCommand: String? = null,
    val lastCommandExitCode: Int? = null,
    val lastCommandOutput: List<String> = emptyList(),
    val commandRunning: Boolean = false,
    val logs: List<String> = emptyList(),
    val lastError: String? = null,
)
