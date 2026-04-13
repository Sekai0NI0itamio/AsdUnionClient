package com.asd.routertunnel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var passwordField: EditText
    private lateinit var portField: EditText
    private lateinit var statusView: TextView
    private lateinit var ipView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        passwordField = findViewById(R.id.passwordField)
        portField = findViewById(R.id.portField)
        statusView = findViewById(R.id.statusView)
        ipView = findViewById(R.id.ipView)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        passwordField.setText(prefs.getString(KEY_PASSWORD, "").orEmpty())
        portField.setText(prefs.getInt(KEY_PORT, DEFAULT_PORT).toString())

        passwordField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.trim().orEmpty()
                prefs.edit().putString(KEY_PASSWORD, value).apply()
            }
        })

        startButton.setOnClickListener {
            val password = passwordField.text.toString().trim()
            val port = portField.text.toString().trim().toIntOrNull() ?: DEFAULT_PORT
            val safePort = if (port in 1..65535) port else DEFAULT_PORT

            prefs.edit()
                .putString(KEY_PASSWORD, password)
                .putInt(KEY_PORT, safePort)
                .apply()

            val intent = Intent(this, TunnelService::class.java).apply {
                action = TunnelService.ACTION_START
            }
            startForegroundService(intent)
            refreshStatus()
        }

        stopButton.setOnClickListener {
            val intent = Intent(this, TunnelService::class.java).apply {
                action = TunnelService.ACTION_STOP
            }
            startService(intent)
            refreshStatus()
        }

        ipView.text = "Phone IP: ${NetUtils.getLocalIp() ?: "unknown"}"
        refreshStatus()
    }

    private fun refreshStatus() {
        val running = TunnelService.isRunning
        val port = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PORT, DEFAULT_PORT)
        statusView.text = if (running) {
            "Status: Running (port $port)"
        } else {
            "Status: Stopped"
        }
    }

    companion object {
        const val PREFS = "router_tunnel_prefs"
        const val KEY_PASSWORD = "password"
        const val KEY_PORT = "port"
        const val DEFAULT_PORT = 45454
    }
}
