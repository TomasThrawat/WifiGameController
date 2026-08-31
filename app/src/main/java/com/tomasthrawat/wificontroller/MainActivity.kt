package com.tomasthrawat.wificontroller

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var statusText: TextView
    private lateinit var deviceListView: ListView

    // ip -> "name\nip" label, keeps insertion order stable for the adapter/list index mapping
    private val discovered = LinkedHashMap<String, DiscoveredDevice>()
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private var discoveryJob: Job? = null

    private data class DiscoveredDevice(val name: String, val ip: String, val port: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("wifi_controller_prefs", MODE_PRIVATE)
        ipInput = findViewById(R.id.ipInput)
        portInput = findViewById(R.id.portInput)
        statusText = findViewById(R.id.statusText)
        deviceListView = findViewById(R.id.deviceListView)

        ipInput.setText(prefs.getString("host_ip", ""))
        portInput.setText(prefs.getString("host_port", "5555"))

        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        deviceListView.adapter = deviceAdapter
        deviceListView.setOnItemClickListener { _, _, position, _ ->
            val device = discovered.values.toList().getOrNull(position) ?: return@setOnItemClickListener
            connectTo(device.ip, device.port)
        }

        findViewById<Button>(R.id.connectButton).setOnClickListener {
            onConnectClicked()
        }
    }

    override fun onResume() {
        super.onResume()
        updateWifiStatus()
        discovered.clear()
        deviceAdapter.clear()
        if (NetworkUtils.isConnectedToWifi(this)) {
            discoveryJob = lifecycleScope.launch(Dispatchers.IO) { listenForBeacons() }
        }
    }

    override fun onPause() {
        super.onPause()
        discoveryJob?.cancel()
        discoveryJob = null
    }

    private suspend fun listenForBeacons() {
        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(BEACON_PORT))
            }
        } catch (_: Exception) {
            return
        }
        try {
            val buf = ByteArray(512)
            while (currentCoroutineContext().isActive) {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val parts = msg.split("|")
                if (parts.size == 4 && parts[0] == "WGCTV1") {
                    val name = parts[1]
                    val ip = parts[2]
                    val port = parts[3].toIntOrNull() ?: continue
                    withContext(Dispatchers.Main) { addOrUpdateDevice(name, ip, port) }
                }
            }
        } catch (_: Exception) {
        } finally {
            socket.close()
        }
    }

    private fun addOrUpdateDevice(name: String, ip: String, port: Int) {
        discovered[ip] = DiscoveredDevice(name, ip, port)
        deviceAdapter.clear()
        deviceAdapter.addAll(discovered.values.map { "${it.name}\n${it.ip}" })
    }

    private fun updateWifiStatus() {
        statusText.text = if (NetworkUtils.isConnectedToWifi(this)) {
            getString(R.string.wifi_connected)
        } else {
            getString(R.string.wifi_not_connected)
        }
    }

    private fun onConnectClicked() {
        if (!NetworkUtils.isConnectedToWifi(this)) {
            Toast.makeText(this, getString(R.string.wifi_required), Toast.LENGTH_LONG).show()
            return
        }

        val host = ipInput.text.toString().trim()
        if (!NetworkUtils.isValidIpFormat(host)) {
            Toast.makeText(this, getString(R.string.invalid_ip), Toast.LENGTH_LONG).show()
            return
        }

        val portText = portInput.text.toString().trim()
        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            Toast.makeText(this, getString(R.string.invalid_port), Toast.LENGTH_LONG).show()
            return
        }

        connectTo(host, port)
    }

    private fun connectTo(host: String, port: Int) {
        prefs.edit()
            .putString("host_ip", host)
            .putString("host_port", port.toString())
            .apply()

        val intent = Intent(this, ControllerActivity::class.java)
        intent.putExtra("host", host)
        intent.putExtra("port", port)
        startActivity(intent)
    }

    companion object {
        // Must match ReceiverService.BEACON_PORT in the WifiGameReceiver (TV) app.
        private const val BEACON_PORT = 8767
    }
}
