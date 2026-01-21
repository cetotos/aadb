package com.torpos.aadb.native

import android.content.Context

object AdbNative {
    init {
        System.loadLibrary("adbhost_jni")
    }

    @Volatile
    private var initialized = false

    fun initialize(context: Context): Boolean {
        if (initialized) return true
        val ok = nativeInit(
            context.filesDir.absolutePath,
            context.cacheDir.absolutePath,
            "localabstract:adbhost"
        )
        if (ok) {
            initialized = true
        }
        return ok
    }

    fun startServer(): String = nativeStartServer()

    fun stopServer(): String = nativeStopServer()

    fun pair(address: String, code: String): String = nativePair(address, code)

    fun connect(address: String): String = nativeConnect(address)

    fun disconnect(address: String?): String = nativeDisconnect(address ?: "")

    fun runCommandStreaming(args: Array<String>): Int = nativeRunCommandStreaming(args)

    private external fun nativeInit(baseDir: String, cacheDir: String, socketSpec: String): Boolean
    private external fun nativeStartServer(): String
    private external fun nativeStopServer(): String
    private external fun nativePair(address: String, code: String): String
    private external fun nativeConnect(address: String): String
    private external fun nativeDisconnect(address: String): String
    private external fun nativeRunCommandStreaming(args: Array<String>): Int
}
