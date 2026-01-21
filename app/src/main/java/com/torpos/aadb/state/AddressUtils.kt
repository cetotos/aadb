package com.torpos.aadb.state

import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

fun parseHostPort(input: String): Pair<String, Int>? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    val host: String
    val portString: String
    if (trimmed.startsWith("[")) {
        val end = trimmed.indexOf(']')
        if (end <= 0 || end + 2 > trimmed.length || trimmed[end + 1] != ':') return null
        host = trimmed.substring(1, end)
        portString = trimmed.substring(end + 2)
    } else {
        val colon = trimmed.lastIndexOf(':')
        if (colon <= 0 || colon == trimmed.length - 1) return null
        host = trimmed.substring(0, colon)
        portString = trimmed.substring(colon + 1)
    }

    val port = portString.toIntOrNull() ?: return null
    if (port !in 1..65535) return null
    if (host.isBlank()) return null
    return host to port
}

fun isSelfHost(host: String): Boolean {
    val normalized = host.trim()
    if (normalized.equals("localhost", ignoreCase = true)) return true
    if (normalized == "127.0.0.1" || normalized == "::1") return true
    return getLocalAddresses().contains(normalized)
}

private fun getLocalAddresses(): Set<String> {
    val addresses = mutableSetOf<String>()
    val interfaces = NetworkInterface.getNetworkInterfaces() ?: return addresses
    for (iface in Collections.list(interfaces)) {
        for (addr in Collections.list(iface.inetAddresses)) {
            if (addr.isMulticastAddress) continue
            if (addr is InetAddress) {
                val hostAddress = addr.hostAddress ?: continue
                addresses.add(hostAddress)
            }
        }
    }
    return addresses
}
