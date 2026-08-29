package com.tomasthrawat.wificontroller

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("wifi_controller_prefs", MODE_PRIVATE)
        ipInput = findViewById(R.id.ipInput)
        portInput = findViewById(R.id.portInput)
        statusText = findViewById(R.id.statusText)

        ipInput.setText(prefs.getString("host_ip", ""))
        portInput.setText(prefs.getString("host_port", "5555"))

        findViewById<Button>(R.id.connectButton).setOnClickListener {
            onConnectClicked()
        }
    }

    override fun onResume() {
        super.onResume()
        updateWifiStatus()
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
        val portText = portInput.text.toString().trim()

        if (!NetworkUtils.isValidIpFormat(host)) {
            Toast.makeText(this, getString(R.string.invalid_ip), Toast.LENGTH_LONG).show()
            return
        }

        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            Toast.makeText(this, getString(R.string.invalid_port), Toast.LENGTH_LONG).show()
            return
        }

        prefs.edit()
            .putString("host_ip", host)
            .putString("host_port", portText)
            .apply()

        val intent = Intent(this, ControllerActivity::class.java)
        intent.putExtra("host", host)
        intent.putExtra("port", port)
        startActivity(intent)
    }
}
