package com.tomasthrawat.wificontroller

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ControllerActivity : AppCompatActivity() {

    private lateinit var host: String
    private var port: Int = 0

    private val sender = UdpSender()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var joystickJob: Job? = null

    private var lastJoyX = 0f
    private var lastJoyY = 0f

    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private var callbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            handleWifiLost()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                handleWifiLost()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        host = intent.getStringExtra("host") ?: ""
        port = intent.getIntExtra("port", 0)

        if (!NetworkUtils.isConnectedToWifi(this)) {
            Toast.makeText(this, getString(R.string.wifi_required), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        scope.launch { sender.open() }

        setupButtons()
        setupJoystick()

        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
        callbackRegistered = true
    }

    private fun handleWifiLost() {
        runOnUiThread {
            if (!isFinishing) {
                Toast.makeText(this, getString(R.string.wifi_lost), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupButtons() {
        val buttonMap = mapOf(
            R.id.btnA to "A",
            R.id.btnB to "B",
            R.id.btnX to "X",
            R.id.btnY to "Y",
            R.id.btnStart to "START",
            R.id.btnSelect to "SELECT",
            R.id.btnUp to "UP",
            R.id.btnDown to "DOWN",
            R.id.btnLeft to "LEFT",
            R.id.btnRight to "RIGHT"
        )

        for ((viewId, code) in buttonMap) {
            findViewById<Button>(viewId).setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> sendCommand("$code:1")
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> sendCommand("$code:0")
                }
                false
            }
        }
    }

    private fun setupJoystick() {
        val joystick = findViewById<JoystickView>(R.id.joystick)

        joystick.onMove = { x, y ->
            lastJoyX = x
            lastJoyY = y
            if (joystickJob?.isActive != true) {
                startJoystickLoop()
            }
        }

        joystick.onRelease = {
            joystickJob?.cancel()
            joystickJob = null
            sendCommand("JOY:0.0,0.0")
        }
    }

    private fun startJoystickLoop() {
        joystickJob = scope.launch {
            while (isActive) {
                sendCommand("JOY:${lastJoyX},${lastJoyY}")
                delay(50)
            }
        }
    }

    private fun sendCommand(message: String) {
        if (host.isEmpty() || port == 0) return
        scope.launch {
            sender.send(host, port, message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (callbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (_: IllegalArgumentException) {
                // already unregistered
            }
        }
        joystickJob?.cancel()
        scope.launch {
            sender.close()
        }
    }
}
