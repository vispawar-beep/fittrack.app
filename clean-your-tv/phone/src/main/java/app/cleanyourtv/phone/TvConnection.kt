package app.cleanyourtv.phone

import android.content.Context
import dadb.AdbKeyPair
import dadb.AdbShellResponse
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One ADB-over-Wi-Fi connection to the TV. The phone keeps its own ADB key, so
 * the TV asks "Allow USB debugging?" once; tick "Always allow from this computer".
 */
class TvConnection(private val context: Context) {
    private var dadb: Dadb? = null

    val isConnected: Boolean get() = dadb != null

    private fun keyPair(): AdbKeyPair {
        val dir = File(context.filesDir, "adb").apply { mkdirs() }
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    suspend fun connect(host: String, port: Int): String = withContext(Dispatchers.IO) {
        close()
        val conn = Dadb.create(host, port, keyPair(), CONNECT_TIMEOUT_MS, 0)
        try {
            val r = conn.shell(app.cleanyourtv.core.Commands.GET_PROPS)
            dadb = conn
            r.output
        } catch (e: Exception) {
            runCatching { conn.close() }
            throw e
        }
    }

    suspend fun shell(command: String): AdbShellResponse = withContext(Dispatchers.IO) {
        val conn = dadb ?: error("Not connected to the TV")
        conn.shell(command)
    }

    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val conn = dadb ?: error("Not connected to the TV")
        conn.install(apk, "-r")
    }

    fun close() {
        runCatching { dadb?.close() }
        dadb = null
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8000
    }
}
