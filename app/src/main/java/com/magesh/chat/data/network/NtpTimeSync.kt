package com.magesh.chat.data.network

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Lightweight SNTP (RFC 4330) client using UDP port 123.
 * Synchronizes room timestamps across devices so live voice chunks and
 * Listen-Together song sync stay aligned even if phone clocks differ.
 */
object NtpTimeSync {
    private const val NTP_PORT = 123
    private const val NTP_PACKET_SIZE = 48
    private const val NTP_MODE_CLIENT = 3
    private const val NTP_VERSION = 3
    private const val OFFSET_1900_TO_1970 = 2_208_988_800L

    private val NTP_HOSTS = listOf("time.google.com", "pool.ntp.org", "time.cloudflare.com")

    @Volatile private var baseNtpMs: Long = 0L
    @Volatile private var baseElapsedMs: Long = 0L
    @Volatile private var isSynced: Boolean = false

    fun syncAsync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            for (host in NTP_HOSTS) {
                val ntpMs = queryNtpServer(host)
                if (ntpMs != null) {
                    baseNtpMs = ntpMs
                    baseElapsedMs = SystemClock.elapsedRealtime()
                    isSynced = true
                    break
                }
            }
        }
    }

    fun nowMs(): Long {
        return if (isSynced) {
            baseNtpMs + (SystemClock.elapsedRealtime() - baseElapsedMs)
        } else {
            System.currentTimeMillis()
        }
    }

    private fun queryNtpServer(host: String): Long? {
        return runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = 2000
                val address = InetAddress.getByName(host)
                val buffer = ByteArray(NTP_PACKET_SIZE)
                buffer[0] = (NTP_MODE_CLIENT or (NTP_VERSION shl 3)).toByte()

                val reqTimeElapsed = SystemClock.elapsedRealtime()
                val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)
                socket.send(request)

                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val respTimeElapsed = SystemClock.elapsedRealtime()

                // Transmit timestamp sits at bytes 40..47
                val seconds = readUnsignedInt(buffer, 40)
                val fraction = readUnsignedInt(buffer, 44)
                val ntpUtcMs = ((seconds - OFFSET_1900_TO_1970) * 1000L) + ((fraction * 1000L) ushr 32)
                val roundTripHalf = (respTimeElapsed - reqTimeElapsed) / 2L
                ntpUtcMs + roundTripHalf
            }
        }.getOrNull()
    }

    private fun readUnsignedInt(buffer: ByteArray, offset: Int): Long {
        val b0 = buffer[offset].toLong() and 0xFF
        val b1 = buffer[offset + 1].toLong() and 0xFF
        val b2 = buffer[offset + 2].toLong() and 0xFF
        val b3 = buffer[offset + 3].toLong() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }
}
