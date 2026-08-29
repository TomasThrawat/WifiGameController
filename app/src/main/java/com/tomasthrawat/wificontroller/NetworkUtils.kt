package com.tomasthrawat.wificontroller

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object NetworkUtils {

    fun isConnectedToWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isValidIpFormat(ip: String): Boolean {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val n = part.toIntOrNull()
            n != null && n in 0..255
        }
    }
}

class UdpSender {

    private var socket: DatagramSocket? = null

    fun open() {
        socket = DatagramSocket()
    }

    fun send(host: String, port: Int, message: String) {
        val s = socket ?: return
        try {
            val data = message.toByteArray(Charsets.UTF_8)
            val address = InetAddress.getByName(host)
            val packet = DatagramPacket(data, data.size, address, port)
            s.send(packet)
        } catch (_: Exception) {
            // UDP is fire-and-forget — a dropped packet is not fatal, just skip it
        }
    }

    fun close() {
        socket?.close()
        socket = null
    }
}
