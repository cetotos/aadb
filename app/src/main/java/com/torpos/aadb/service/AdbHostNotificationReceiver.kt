package com.torpos.aadb.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.torpos.aadb.state.AdbHostRepository
import com.torpos.aadb.state.isSelfHost
import com.torpos.aadb.state.parseHostPort

class AdbHostNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AdbHostNotification.ACTION_PAIR_INPUT) return
        AdbHostRepository.initialize(context.applicationContext)

        val input = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AdbHostNotification.KEY_PAIR_INPUT)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (input.isEmpty()) {
            AdbHostRepository.reportError("Pairing input is empty.")
            AdbHostRepository.logInfo("Pairing rejected: empty input.")
            AdbHostNotification.post(context)
            return
        }

        val code = parsePairingCode(input)
        val address = AdbHostRepository.state.value.pairingAddress.takeIf { it.isNotBlank() }

        if (address == null || code == null) {
            AdbHostRepository.reportError("Pairing code required. Make sure the pairing service is detected.")
            AdbHostRepository.logInfo("Pairing rejected: invalid input.")
            AdbHostNotification.post(context)
            return
        }

        val host = parseHostPort(address)?.first
        if (host == null || !isSelfHost(host)) {
            AdbHostRepository.reportError("Only connections to this device are allowed.")
            AdbHostRepository.logInfo("Pairing rejected: non-local address.")
            AdbHostNotification.post(context)
            return
        }

        AdbHostRepository.updatePairingAddress(address)
        AdbHostRepository.updatePairingCode(code)
        AdbHostRepository.requestPair(address, code)
        AdbHostNotification.post(context)
    }

    private fun parsePairingCode(input: String): String? {
        val normalized = input.trim()
        return if (normalized.length == 6 && normalized.all { it.isDigit() }) {
            normalized
        } else {
            null
        }
    }
}
