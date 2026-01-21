package com.torpos.aadb.state

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import com.torpos.aadb.service.AdbHostNotification
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket

object AdbServiceDiscovery {
    private const val PAIRING_SERVICE = "_adb-tls-pairing._tcp"
    private const val CONNECT_SERVICE = "_adb-tls-connect._tcp"

    private var pairingListener: NsdManager.DiscoveryListener? = null
    private var connectListener: NsdManager.DiscoveryListener? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(context: Context) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return
        if (pairingListener == null) {
            pairingListener = createListener(context, nsd, PAIRING_SERVICE) { host, port ->
                AdbHostRepository.setDetectedPairingAddress("127.0.0.1:$port")
                AdbHostNotification.post(context)
            }
            runCatching {
                nsd.discoverServices(PAIRING_SERVICE, NsdManager.PROTOCOL_DNS_SD, pairingListener)
            }.onFailure {
                AdbHostRepository.reportError("Pairing discovery failed: ${it.message}")
            }
        }
        if (connectListener == null) {
            connectListener = createListener(context, nsd, CONNECT_SERVICE) { host, port ->
                AdbHostRepository.setDetectedConnectAddress("127.0.0.1:$port")
                AdbHostNotification.post(context)
            }
            runCatching {
                nsd.discoverServices(CONNECT_SERVICE, NsdManager.PROTOCOL_DNS_SD, connectListener)
            }.onFailure {
                AdbHostRepository.reportError("Connect discovery failed: ${it.message}")
            }
        }
    }

    fun stop(context: Context) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return
        pairingListener?.let {
            runCatching { nsd.stopServiceDiscovery(it) }
        }
        connectListener?.let {
            runCatching { nsd.stopServiceDiscovery(it) }
        }
        pairingListener = null
        connectListener = null
    }

    private fun createListener(
        context: Context,
        nsd: NsdManager,
        serviceType: String,
        onResolved: (String, Int) -> Unit
    ): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                // Keep last known address while discovery restarts to avoid "waiting" flicker.
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val resolvedType = serviceInfo.serviceType.trimEnd('.')
                val expectedType = serviceType.trimEnd('.')
                if (resolvedType != expectedType) return
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (serviceType == PAIRING_SERVICE) {
                            AdbHostRepository.logInfo("Pairing discovery failed ($errorCode)")
                        } else {
                            AdbHostRepository.logInfo("Connect discovery failed ($errorCode)")
                        }
                        AdbHostNotification.post(context)
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val port = resolved.port
                        if (!isLocalInterface(host)) return
                        if (!isPortInUse(port)) return
                        mainHandler.post { onResolved(host, port) }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (serviceType == PAIRING_SERVICE) {
                    AdbHostRepository.setDetectedPairingAddress("")
                } else {
                    AdbHostRepository.setDetectedConnectAddress("")
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                // no-op
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                AdbHostRepository.reportError("Service discovery failed ($errorCode).")
                AdbHostNotification.post(context)
                runCatching { nsd.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                AdbHostRepository.logInfo("Service discovery stop failed ($errorCode)")
                AdbHostNotification.post(context)
                runCatching { nsd.stopServiceDiscovery(this) }
            }
        }
    }

    private fun isLocalInterface(address: String): Boolean {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
        return interfaces.asSequence().any { iface ->
            iface.inetAddresses.asSequence().any { it.hostAddress == address }
        }
    }

    private fun isPortInUse(port: Int): Boolean {
        return try {
            ServerSocket().use {
                it.bind(InetSocketAddress("127.0.0.1", port), 1)
            }
            false
        } catch (_: IOException) {
            true
        }
    }
}
