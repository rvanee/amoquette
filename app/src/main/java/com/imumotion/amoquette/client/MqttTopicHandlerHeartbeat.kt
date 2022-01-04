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

package com.imumotion.amoquette.client

import android.util.Log
import com.imumotion.amoquette.*
import java.util.*
import kotlin.concurrent.timerTask

class MqttTopicHandlerHeartbeat(manager: MqttTopicManager) :
    MqttTopicHandler(TOPIC_HEARTBEAT, manager) {
    val LOGTAG = this.javaClass.name
    private var mTimer: Timer? = null

    private var mNumberSent = 0L

    override fun onConnected() {
        // Check if heartbeat enabled, i.e. if interval > 0.
        if(getInterval() > 0L) {  // interval == 0 means heartbeats disabled
            // Send first heartbeat message after MQTT connection established,
            // with an interval delay to make sure subscriptions have been
            // setup appropriately (this happens in MqttClient:defaultCbConnect).
            mTimer = Timer(this.javaClass.name)  // Create new timer
            mNumberSent = 0
            sendNextHeartbeat()  // Jumpstart heartbeats
        }
    }

    override fun onDisconnect() {
        Log.d(LOGTAG, "Cancel scheduled heartbeat")
        mTimer?.cancel()
    }

    // Send next heartbeat in the future
    private fun sendNextHeartbeat() {
        val interval = getInterval()
        mTimer?.schedule(timerTask {
            val heartbeatMap = createHeader()
            heartbeatMap[HEARTBEAT_INTERVAL] = interval
            heartbeatMap[HEARTBEAT_NUMBER] = mNumberSent
            sendMessage(TOPIC_HEARTBEAT, heartbeatMap)
            mNumberSent++
        }, interval)
    }

    private fun getInterval(): Long {
        return (mManager.getProperty(BROKER_PROPERTY_HEARTBEAT_INTERVAL) as String).toLong()
    }

    override fun processMessage(message: String?, topic: String) : Map<String, Any> {
        // As a first step, retrieve current timestamp
        val timestampReceived = getCurrentTimestamp()

        // Return map
        var returnmap = mutableMapOf<String, Any>()

        // Unpack message and calculate latency
        val map = getMapFromJson(message)
        if(map.isEmpty()) {
            throw Exception("Empty $TOPIC_HEARTBEAT message received!")
        } else {
            val source = map[MESSAGE_FIELD_SOURCE] as String

            // Only process heartbeats that we generated ourselves
            if(source == MQTTCLIENT_ID) {
                // gson converts every number to Double -> convert back to Long
                // if it is indeed a Double
                val timestampField = map[MESSAGE_FIELD_TIMESTAMP]
                val timestampSent =
                    when (timestampField) {
                        is Double -> timestampField.toLong()
                        is Long -> timestampField
                        else -> 0L
                    }
                val latency = timestampReceived - timestampSent

                // Create and send latency message
                val latencyMap = createHeader()
                latencyMap[MESSAGE_FIELD_SENDER] = source  // Store previous source as sender
                latencyMap[MESSAGE_FIELD_LATENCY] = latency
                sendMessage(TOPIC_LATENCY, latencyMap)

                // Reuse latencyMap as return map
                returnmap = latencyMap

                // Send next heartbeat after a specified interval
                sendNextHeartbeat()
            }
        }

        return returnmap
    }
}
