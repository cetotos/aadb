package com.torpos.aadb.state

import android.content.Context
import com.torpos.aadb.service.AdbHostNotification
import com.torpos.aadb.native.AdbNative
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

object AdbHostRepository {
    private const val MAX_LOGS = 40
    private const val MAX_HISTORY = 30
    private const val MAX_COMMAND_OUTPUT = 200

    private val _state = MutableStateFlow(AdbHostState())
    val state: StateFlow<AdbHostState> = _state.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var backendAvailable = false
    @Volatile
    private var autoConnectAfterPair = false
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (backendAvailable) return
        backendAvailable = AdbNative.initialize(context)
        if (!backendAvailable) {
            reportError("Failed to initialize the native adb backend.")
        }
    }

    fun updatePairingAddress(value: String) {
        _state.update { it.copy(pairingAddress = value, lastError = null) }
    }

    fun updatePairingCode(value: String) {
        _state.update { it.copy(pairingCode = value, lastError = null) }
    }

    fun updateConnectAddress(value: String) {
        _state.update { it.copy(connectAddress = value, lastError = null) }
    }

    fun updateCommandInput(value: String) {
        _state.update { it.copy(commandInput = value, lastError = null) }
    }

    fun setDetectedPairingAddress(value: String) {
        _state.update { it.copy(pairingAddress = value) }
    }

    fun setDetectedConnectAddress(value: String) {
        _state.update { it.copy(connectAddress = value) }
        tryAutoConnect()
    }

    fun startServer() {
        if (!backendAvailable) {
            reportError("ADB host backend is not available in this build.")
            addLog("Server start failed: backend not wired.")
            _state.update { it.copy(serverRunning = false, connectionState = ConnectionState.Error) }
            notifyStatus()
            return
        }
        executor.execute {
            val result = AdbNative.startServer()
            if (result.startsWith("ERROR:")) {
                reportError(result.removePrefix("ERROR:").trim())
                addLog("Server start failed.")
                _state.update { it.copy(serverRunning = false, connectionState = ConnectionState.Error) }
            } else {
                _state.update { it.copy(serverRunning = true, connectionState = ConnectionState.Idle, lastError = null) }
                addLog(result)
            }
            notifyStatus()
        }
    }

    fun stopServer() {
        if (!backendAvailable) {
            reportError("ADB host backend is not available in this build.")
            addLog("Server stop failed: backend not wired.")
            notifyStatus()
            return
        }
        executor.execute {
            val result = AdbNative.stopServer()
            if (result.startsWith("ERROR:")) {
                reportError(result.removePrefix("ERROR:").trim())
                addLog("Server stop failed.")
            } else {
                _state.update { it.copy(serverRunning = false, connectionState = ConnectionState.Idle, lastError = null) }
                addLog(result)
            }
            notifyStatus()
        }
    }

    fun requestPair(pairingAddress: String, pairingCode: String) {
        if (!backendAvailable) {
            reportError("Pairing is not available because the backend is missing.")
            addLog("Pairing requested for $pairingAddress, but backend is missing.")
            _state.update { it.copy(connectionState = ConnectionState.Error) }
            notifyStatus()
            return
        }
        executor.execute {
            _state.update { it.copy(pairingAddress = pairingAddress) }
            _state.update { it.copy(connectionState = ConnectionState.Pairing, lastError = null) }
            val result = AdbNative.pair(pairingAddress, pairingCode)
            if (result.startsWith("ERROR:")) {
                reportError(result.removePrefix("ERROR:").trim())
                addLog("Pairing failed for $pairingAddress.")
                _state.update { it.copy(connectionState = ConnectionState.Error) }
            } else {
                _state.update { it.copy(connectionState = ConnectionState.Paired, lastError = null) }
                addLog(result)
                autoConnectAfterPair = true
                tryAutoConnect()
            }
            notifyStatus()
        }
    }

    fun requestConnect(connectAddress: String) {
        if (!backendAvailable) {
            reportError("Connect is not available because the backend is missing.")
            addLog("Connect requested for $connectAddress, but backend is missing.")
            _state.update { it.copy(connectionState = ConnectionState.Error) }
            notifyStatus()
            return
        }
        executor.execute {
            _state.update { it.copy(connectAddress = connectAddress) }
            val result = AdbNative.connect(connectAddress)
            if (result.startsWith("ERROR:")) {
                reportError(result.removePrefix("ERROR:").trim())
                addLog("Connect failed for $connectAddress.")
                _state.update { it.copy(connectionState = ConnectionState.Error) }
            } else {
                _state.update { it.copy(connectionState = ConnectionState.Connected, lastError = null) }
                addLog(result)
            }
            notifyStatus()
        }
    }

    fun requestDisconnect() {
        if (!backendAvailable) {
            reportError("Disconnect is not available because the backend is missing.")
            addLog("Disconnect requested, but backend is missing.")
            notifyStatus()
            return
        }
        val address = _state.value.connectAddress
        executor.execute {
            val result = AdbNative.disconnect(address.takeIf { it.isNotBlank() })
            if (result.startsWith("ERROR:")) {
                reportError(result.removePrefix("ERROR:").trim())
                addLog("Disconnect failed.")
            } else {
                _state.update { it.copy(connectionState = ConnectionState.Idle, lastError = null) }
                addLog(result)
            }
            notifyStatus()
        }
    }

    fun runAdbCommand(command: String, shellMode: Boolean): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            reportError("Type a command first.")
            return false
        }

        val displayCommand = if (shellMode) {
            "shell $trimmed"
        } else {
            when {
                trimmed.startsWith("adb ") -> trimmed
                trimmed == "adb" -> trimmed
                else -> "adb $trimmed"
            }
        }

        if (!backendAvailable) {
            rejectCommand(displayCommand, "ADB backend isn't available in this build.")
            return false
        }

        if (!state.value.serverRunning) {
            rejectCommand(displayCommand, "Start the server first.")
            return false
        }

        val parseInput = if (shellMode) "adb shell $trimmed" else displayCommand
        val args = parseCommandArguments(parseInput).toList()
        if (args.isEmpty()) {
            rejectCommand(displayCommand, "Please enter a valid adb command.")
            return false
        }

        val commandIndex = findSubcommandIndex(args)
        if (commandIndex == null || commandIndex >= args.size) {
            rejectCommand(displayCommand, "Please enter a valid adb command.")
            return false
        }

        if (!shellMode && containsServerOverride(args, commandIndex)) {
            rejectCommand(displayCommand, "Overriding the adb server address isn't supported here.")
            return false
        }

        val subcommand = commandIndex?.let { args.getOrNull(it) }
        val nextArg = commandIndex?.let { args.getOrNull(it + 1) }

        val effectiveCommand = if (subcommand?.startsWith("wait-for-") == true) {
            args.getOrNull((commandIndex ?: 0) + 1)
        } else {
            subcommand
        }

        val actualCommandIndex = if (subcommand?.startsWith("wait-for-") == true) {
            (commandIndex ?: 0) + 1
        } else {
            commandIndex ?: 0
        }
        val commandArgs = args.drop(actualCommandIndex + 1)
        val commandTarget = when (effectiveCommand) {
            "pair", "connect" -> commandArgs.firstOrNull()
            "disconnect" -> commandArgs.firstOrNull()
            else -> null
        }
        val validationError = validateCommandArgs(effectiveCommand, commandArgs)
        if (validationError != null) {
            rejectCommand(displayCommand, validationError)
            return false
        }

        if (!shellMode && (effectiveCommand == "start-server" || effectiveCommand == "kill-server")) {
            rejectCommand(displayCommand, "Use the Host tab to start or stop the server.")
            return false
        }

        if (!shellMode && (effectiveCommand == "connect" || effectiveCommand == "pair")) {
            val target = if (subcommand?.startsWith("wait-for-") == true) {
                args.getOrNull((commandIndex ?: 0) + 2)
            } else {
                nextArg
            }
            val host = target?.let { parseHostPort(it)?.first }
            if (host == null || !isSelfHost(host)) {
                rejectCommand(displayCommand, "This app only connects to this device.")
                return false
            }
        }

        pushHistory(if (shellMode) trimmed else displayCommand)
        _state.update {
            it.copy(
                lastCommand = displayCommand,
                lastCommandExitCode = null,
                lastCommandOutput = emptyList(),
                commandRunning = true
            )
        }
        addLog("Command running: $displayCommand")
        executor.execute {
            val exitCode = AdbNative.runCommandStreaming(args.toTypedArray())
            val outputLines = _state.value.lastCommandOutput
            if (exitCode == 0) {
                when (effectiveCommand) {
                    "pair" -> {
                        if (commandTarget != null) {
                            _state.update { it.copy(pairingAddress = commandTarget, connectionState = ConnectionState.Paired) }
                        } else {
                            _state.update { it.copy(connectionState = ConnectionState.Paired) }
                        }
                    }
                    "connect" -> {
                        if (commandTarget != null) {
                            _state.update { it.copy(connectAddress = commandTarget, connectionState = ConnectionState.Connected) }
                        } else {
                            _state.update { it.copy(connectionState = ConnectionState.Connected) }
                        }
                    }
                    "disconnect" -> {
                        _state.update { it.copy(connectionState = ConnectionState.Idle) }
                    }
                }
            }
            _state.update { it.copy(lastCommandExitCode = exitCode, commandRunning = false) }
            outputLines.take(12).forEach { addLog(it) }
            if (exitCode != 0) {
                reportError("Command failed with exit code $exitCode.")
                addLog("Command failed: $displayCommand")
            } else {
                addLog("Command completed: $displayCommand")
            }
            notifyStatus()
        }
        return true
    }

    fun reportError(message: String) {
        _state.update { it.copy(lastError = message, connectionState = ConnectionState.Error) }
    }

    fun logInfo(message: String) {
        addLog(message)
    }

    fun clearError() {
        _state.update { it.copy(lastError = null) }
    }

    fun clearCommandOutput() {
        _state.update {
            it.copy(lastCommand = null, lastCommandExitCode = null, lastCommandOutput = emptyList(), commandRunning = false)
        }
    }

    private fun addLog(message: String) {
        _state.update {
            val updated = listOf(message) + it.logs
            it.copy(logs = updated.take(MAX_LOGS))
        }
    }

    private fun pushHistory(command: String) {
        _state.update {
            val updated = listOf(command) + it.commandHistory
            it.copy(commandHistory = updated.take(MAX_HISTORY), commandInput = "")
        }
    }

    private fun rejectCommand(displayCommand: String, message: String) {
        reportError(message)
        _state.update {
            it.copy(
                lastCommand = displayCommand,
                lastCommandExitCode = 1,
                lastCommandOutput = listOf(message),
                commandRunning = false
            )
        }
        addLog("Rejected command: $displayCommand")
    }

    private fun notifyStatus() {
        appContext?.let { AdbHostNotification.post(it) }
    }

    private fun tryAutoConnect() {
        if (!autoConnectAfterPair) return
        val address = _state.value.connectAddress
        val host = parseHostPort(address)?.first
        if (address.isBlank() || host == null || !isSelfHost(host) || !_state.value.serverRunning) return
        if (_state.value.connectionState == ConnectionState.Connected) {
            autoConnectAfterPair = false
            return
        }
        autoConnectAfterPair = false
        requestConnect(address)
    }

    private fun parseCommandArguments(input: String): Array<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var quoteChar = '\"'
        var escape = false

        fun flush() {
            if (current.isNotEmpty()) {
                args.add(current.toString())
                current.setLength(0)
            }
        }

        for (ch in input) {
            if (escape) {
                current.append(ch)
                escape = false
                continue
            }
            when (ch) {
                '\\' -> escape = true
                '\"', '\'' -> {
                    if (inQuotes && ch == quoteChar) {
                        inQuotes = false
                    } else if (!inQuotes) {
                        inQuotes = true
                        quoteChar = ch
                    } else {
                        current.append(ch)
                    }
                }
                ' ', '\n', '\t' -> {
                    if (inQuotes) {
                        current.append(ch)
                    } else {
                        flush()
                    }
                }
                else -> current.append(ch)
            }
        }
        flush()
        if (args.isNotEmpty() && args.first() == "adb") {
            args.removeAt(0)
        }
        return args.toTypedArray()
    }

    private fun containsServerOverride(args: List<String>, commandIndex: Int): Boolean {
        if (commandIndex <= 0) return false
        return args.take(commandIndex).any { arg ->
            arg == "-H" || arg == "-P" || arg == "-L" ||
                arg.startsWith("-H") || arg.startsWith("-P") || arg.startsWith("-L")
        }
    }

    private fun findSubcommandIndex(args: List<String>): Int? {
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (!arg.startsWith("-")) return i
            when {
                arg == "--" -> return i + 1
                arg == "-s" || arg == "-t" || arg == "-H" || arg == "-P" || arg == "-L" ||
                    arg == "--reply-fd" || arg == "--one-device" -> i += 2
                arg.startsWith("-s") || arg.startsWith("-t") || arg.startsWith("-H") ||
                    arg.startsWith("-P") || arg.startsWith("-L") -> i += 1
                arg == "-d" || arg == "-e" || arg == "-a" -> i += 1
                else -> return i
            }
        }
        return null
    }

    private fun parseNativeResult(result: String): Pair<Int, String> {
        val lines = result.split('\n', limit = 2)
        val exitCode = lines.firstOrNull()
            ?.removePrefix("EXIT=")
            ?.toIntOrNull() ?: -1
        val output = if (lines.size > 1) lines[1].trim() else ""
        return exitCode to output
    }

    private fun appendCommandOutput(line: String) {
        val trimmed = line.trimEnd()
        _state.update {
            val updated = (it.lastCommandOutput + trimmed).takeLast(MAX_COMMAND_OUTPUT)
            it.copy(lastCommandOutput = updated)
        }
    }

    @JvmStatic
    fun onNativeOutput(line: String) {
        if (line.isBlank()) return
        appendCommandOutput(line)
    }

    private fun validateCommandArgs(
        command: String?,
        commandArgs: List<String>
    ): String? {
        if (command.isNullOrBlank()) {
            return "Please enter an adb command, like devices or connect."
        }
        return when (command) {
            "devices", "version", "help" -> null
            "root", "unroot" -> null
            "shell" -> if (commandArgs.isNotEmpty()) null else "Shell needs a command here. Try: adb shell getprop"
            "connect" -> if (commandArgs.size == 1) null else "Usage: adb connect HOST[:PORT]"
            "disconnect" -> if (commandArgs.size <= 1) null else "Usage: adb disconnect [HOST[:PORT]]"
            "pair" -> if (commandArgs.size in 1..2) null else "Usage: adb pair HOST[:PORT] [PAIRING CODE]"
            "push" -> if (commandArgs.size >= 2) null else "Usage: adb push <source> <destination>"
            "pull" -> if (commandArgs.isNotEmpty()) null else "Usage: adb pull <remote> [local]"
            "install" -> if (commandArgs.isNotEmpty()) null else "Usage: adb install <apk>"
            "install-multiple" -> if (commandArgs.isNotEmpty()) null else "Usage: adb install-multiple <apk...>"
            "uninstall" -> if (commandArgs.isNotEmpty()) null else "Usage: adb uninstall <package>"
            else -> "That command isn’t supported here yet. Try devices, root, shell, connect, pair, push, pull, install, or uninstall."
        }
    }
}
