/*
 * Copyright (c) 2021 Rene F. van Ee
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0,
 * which accompanies this distribution.
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package com.imumotion.amoquette

import android.content.Intent
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.imumotion.amoquette.broker.Action
import com.imumotion.amoquette.broker.BrokerService
import com.imumotion.amoquette.client.MqttClient
import com.imumotion.amoquette.client.MqttClientState
import kotlinx.coroutines.*
import java.util.*

enum class ServiceAction {
    UNDEFINED,
    STOPPED,
    STARTED,
    PROBING,
    STARTING,
    STOPPING
}

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    val LOGTAG = this.javaClass.name
    private val gson = Gson()

    private var backPressedTime = 0L

    private var serviceAction = ServiceAction.UNDEFINED
    private var client: MqttClient? = null

    internal fun updateStateMachine() {
        fun logStateError(clientState: MqttClientState) {
            Log.e(LOGTAG, "updateStateMachine: Illegal combination: " +
                    "serviceAction=${serviceAction.name}, " +
                    "client state=${clientState.name}")
        }

        val clientstate = client?.getState() ?: MqttClientState.UNDEFINED
        when(serviceAction) {
            ServiceAction.STOPPED -> {
                if(clientstate != MqttClientState.DISCONNECTED) {
                    logStateError(clientstate)
                }
            }
            ServiceAction.STARTED -> {
                when(clientstate) {
                    MqttClientState.CONNECTED -> { /* Do nothing, totally OK */ }
                    MqttClientState.DISCONNECTED -> {
                        // Do nothing, will happen when switching to Settings Activity
                    }
                    else -> logStateError(clientstate)
                }
            }
            ServiceAction.PROBING -> {
                when(clientstate) {
                    MqttClientState.CREATED -> {
                        connectClient()  // Try to connect
                    }
                    MqttClientState.CONNECTING -> showServiceTransition()
                    MqttClientState.CONNECTFAILED -> {
                        showServiceStopped()
                        disconnectClient()  // In order to set its state to DISCONNECTED
                        serviceAction = ServiceAction.STOPPED
                    }
                    MqttClientState.CONNECTED -> {
                        showServiceStarted()
                        serviceAction = ServiceAction.STARTED
                    }
                    else -> {
                        logStateError(clientstate)
                    }
                }
            }
            ServiceAction.STARTING -> {
                when(clientstate) {
                    MqttClientState.DISCONNECTED -> {
                        startService()
                        connectClient()  // Try to connect
                    }
                    MqttClientState.CONNECTING -> showServiceTransition()
                    MqttClientState.CONNECTFAILED -> {
                        showServiceTransition()
                        connectClient()  // Try again, we expect to have the service up and
                                         // running soon
                    }
                    MqttClientState.CONNECTED -> {
                        showServiceStarted()
                        serviceAction = ServiceAction.STARTED
                    }
                    else -> {
                        logStateError(clientstate)
                    }
                }
            }
            ServiceAction.STOPPING -> {
                when(clientstate) {
                    MqttClientState.CONNECTED -> {
                        showServiceTransition()
                        stopService()
                    }
                    MqttClientState.CONNECTIONLOST -> {
                        showServiceStopped()
                        disconnectClient()  // In order to set its state to DISCONNECTED
                        serviceAction = ServiceAction.STOPPED
                    }
                    else -> {
                        logStateError(clientstate)
                    }
                }
            }
            else -> {
                logStateError(clientstate)
            }
        }
    }

    // External entry point (from MqttClient)
    // Receives key/value pairs from the Mqtt client, to be used for updating UI etc.
    fun processMap(map: Map<String, Map<String, Any>>) {
        launch {
            map.forEach { topicmap ->
                topicmap.value.forEach {
                    // Create id name by concatenating topic (stripped of $, with
                    // forward slashes / replaced by underscore _ and converted to lowercase)
                    // and the key from the topicmap:
                    val idName = "${topicmap.key}_${it.key}".lowercase().
                                    replace("/", "_").replace("\$", "")

                    val id = resources.getIdentifier(idName, "id", packageName)
                    if (id != 0) {
                        val strvalue = if (it.value is Double) String.format("%.2f", it.value)
                                       else it.value.toString()
                        findViewById<TextView>(id).text = strvalue
                    }
                }
            }
        }
    }

    // Actions
    private fun startService() {
        // Retrieve preferences/settings
        val brokerproperties = PreferenceManager.getDefaultSharedPreferences(this).all
        // Start service
        Intent(applicationContext, BrokerService::class.java).also {
            it.action = Action.START.name
            it.putExtra(CONST_JSONSTRINGDATA, gson.toJson(brokerproperties).toString())
            startForegroundService(it)
        }
    }
    private fun stopService() {
        // Stop the broker
        Intent(applicationContext, BrokerService::class.java).also {
            it.action = Action.STOP.name
            startForegroundService(it)
        }
    }
    private fun connectClient() {
        client?.connect("", "")
    }
    private fun disconnectClient() {
        client?.disconnect()
    }

    // Activity life cycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serviceAction = ServiceAction.UNDEFINED

        title = "AMoQueTTe Broker"

        // If no settings available, start the Settings activity. This will set default
        // values and give the user the opportunity to review and change them.
        val preferences = PreferenceManager.getDefaultSharedPreferences(this).all
        if (preferences.isEmpty())
        {
            // Start settings activity
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val brokerButton = findViewById<ImageButton>(R.id.brokerActiveButton)
        brokerButton.setOnClickListener {
            when(serviceAction) {
                ServiceAction.STARTED -> {
                    serviceAction = ServiceAction.STOPPING
                    updateStateMachine()
                }
                ServiceAction.STOPPED -> {
                    serviceAction = ServiceAction.STARTING
                    updateStateMachine()
                }
                else -> {
                    // Do nothing
                }
            }
        }
    }

    override fun onStop() {
        if(client?.isConnected() == true) {
            client!!.disconnect()
        }
        super.onStop()
    }

    override fun onStart() {
        super.onStart()

        val properties = Properties()
        PreferenceManager.getDefaultSharedPreferences(this).all.forEach {
            properties.set(it.key, it.value?.toString())
        }
        val host = when(properties["host"]) {
            "0.0.0.0" -> "localhost"
            else -> properties["host"]
        }
        val connectionString = "tcp://$host:${properties["port"]}"

        client = object: MqttClient(
            this, MQTTCLIENT_ID, properties, connectionString) {
            override fun onConnectionStatusChanged() {
                updateStateMachine()
            }
        }

        // Probe current service status by trying to connect once.
        // If it succeeds, the service is active, otherwise it is not.
        serviceAction = ServiceAction.PROBING
        updateStateMachine()
    }

    // Status updates on the GUI
    private fun showServiceTransition() {
        val brokerButton = findViewById<ImageButton>(R.id.brokerActiveButton)
        brokerButton.setImageResource(R.drawable.ic_powerbutton_transition)
        findViewById<TextView>(R.id.connStringTextView).text = "N/A"
    }
    private fun showServiceStopped() {
        val brokerButton = findViewById<ImageButton>(R.id.brokerActiveButton)
        brokerButton.setImageResource(R.drawable.ic_powerbutton_off)
        findViewById<TextView>(R.id.connStringTextView).text = "N/A"
    }
    private fun showServiceStarted() {
        val brokerButton = findViewById<ImageButton>(R.id.brokerActiveButton)
        brokerButton.setImageResource(R.drawable.ic_powerbutton_on)
        launch {
            displayBrokerConnectionString()
        }
    }
    internal suspend fun displayBrokerConnectionString() {
        withContext(Dispatchers.IO) {
            val wifiMgr = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ipULong = wifiMgr.connectionInfo.ipAddress.toULong()
            val ipbytes = ShortArray(4) {i -> ((ipULong shr (i shl 3)) and 255u).toShort()}
            val ipstr = ipbytes.joinToString(separator = ".")

            val brokerProperties =
                PreferenceManager.getDefaultSharedPreferences(applicationContext).all
            val port = brokerProperties["port"]
            val connectionstring = "tcp://$ipstr:$port"

            val connStringTextView: TextView = findViewById(R.id.connStringTextView)
            connStringTextView.text = connectionstring
        }
    }

    // Options menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Kill application after two consecutive back presses. Note that this does not influence
    // the service state!
    override fun onBackPressed() {
        // BackPressed twice within 2 seconds?
        if (backPressedTime + 2000L > System.currentTimeMillis()) {
            super.onBackPressed()
            finishAffinity()
        } else {
            Toast.makeText(baseContext, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
        backPressedTime = System.currentTimeMillis()
    }
}