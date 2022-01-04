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
import com.imumotion.amoquette.MainActivity
import com.imumotion.amoquette.TOPIC_TOPLEVEL
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.util.*

enum class MqttClientState {
    UNDEFINED,
    CREATED,
    DISCONNECTED,
    CONNECTING,
    CONNECTFAILED,
    CONNECTED,
    CONNECTIONLOST,
    DISCONNECTFAILED
}

open class MqttClient(mainActivity: MainActivity,
                      clientId: String,
                      properties: Properties,
                      serverUri: String = "tcp://localhost:1883") {
    val LOGTAG = this.javaClass.name

    private val mqttClient = MqttAndroidClient(mainActivity.applicationContext, serverUri, clientId)
    private val mTopicManager = MqttTopicManager(this)
    private val mProperties = properties
    private val mMainActivity = mainActivity  // TODO replace with some form of function pointer

    private var mState = MqttClientState.CREATED

    private val defaultCbConnect = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            setState(MqttClientState.CONNECTED)
            Log.i(LOGTAG, "Connection success (clientId=${mqttClient.clientId} " +
                    "serverURI=${mqttClient.serverURI})")

            // Subscriptions
            mTopicManager.getAllTopics().forEach { subscribe(it) }

            // Notify handlers that the connection has been established
            mTopicManager.onConnected()
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            setState(MqttClientState.CONNECTFAILED)
        }
    }
    private val defaultCbClient = object : MqttCallbackExtended {
        override fun messageArrived(topic: String?, message: MqttMessage?) {
            // Send cleaned topic and message to topic manager
            val map = mTopicManager.onMessageReceived(removeRootTopic(topic), message?.toString())
            mMainActivity.processMap(map)  // TODO improve this mechanism
        }

        override fun connectionLost(cause: Throwable?) {
            if(!isState(MqttClientState.DISCONNECTED)) { // Meaningless if already disconnected
                setState(MqttClientState.CONNECTIONLOST)
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {
        }

        /**
         * Called when the connection to the server is completed successfully.
         * @param reconnect If true, the connection was the result of automatic reconnect.
         * @param serverURI The server URI that the connection was made to.
         */
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            setState(MqttClientState.CONNECTED)
            //Log.d(LOGTAG, "Connect complete to server $serverURI (reconnect=$reconnect)")
        }
    }
    private val defaultCbSubscribe = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            //Log.d(LOGTAG, "Subscribed to topic")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            //Log.d(LOGTAG, "Failed to subscribe topic")
        }
    }
    private val defaultCbUnsubscribe = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            //Log.d(LOGTAG, "Unsubscribed to topic")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            //Log.d(LOGTAG, "Failed to unsubscribe topic")
        }
    }
    private val defaultCbPublish = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            //Log.d(LOGTAG, "Message published to topic")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            //Log.d(LOGTAG, "Failed to publish message to topic")
        }
    }
    private val defaultCbDisconnect = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            setState(MqttClientState.DISCONNECTED)
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            setState(MqttClientState.DISCONNECTFAILED)
        }
    }

    fun connect(username:   String               = "",
                password:   String               = "",
                cbConnect:  IMqttActionListener  = defaultCbConnect,
                cbClient:   MqttCallback         = defaultCbClient) : MqttClientState {
        mqttClient.setCallback(cbClient)

        val options = MqttConnectOptions()
        options.userName = username
        options.password = password.toCharArray()

        // Do not reconnect - we don't expect to lose connection to a broker on the same
        // device, other than because of deliberate action by the user
        options.setAutomaticReconnect(false)

        setState(MqttClientState.CONNECTING)

        try {
            mqttClient.connect(options, null, cbConnect)
        } catch (e: MqttException) {
            setState(MqttClientState.CONNECTFAILED)
        }

        return getState()
    }

    fun isConnected(): Boolean {
        return mqttClient.isConnected
    }

    fun getState(): MqttClientState {
        return mState
    }

    fun isState(state: MqttClientState): Boolean {
        return getState().equals(state)
    }

    internal fun setState(newState: MqttClientState) {
        val oldState = mState
        Log.d(LOGTAG, "State transition from ${oldState.name} to ${newState.name}")
        mState = newState

        if(oldState != newState) {
            onConnectionStatusChanged()
        }
    }

    open fun onConnectionStatusChanged() {}

    fun addRootTopic(topic: String) : String {
        // Do not add root topic for $SYS topics
        return if(topic.startsWith("\$SYS")) topic else "$TOPIC_TOPLEVEL/$topic"
    }

    fun removeRootTopic(topic: String?) : String {
        return topic?.replace("$TOPIC_TOPLEVEL/", "") ?: ""
    }

    fun subscribe(topic:        String,
                  qos:          Int                 = 1,
                  cbSubscribe:  IMqttActionListener = defaultCbSubscribe) {
        try {
            val includingRootTopic = addRootTopic(topic)
            mqttClient.subscribe(includingRootTopic, qos, null, cbSubscribe)
            Log.d(LOGTAG, "Subscribed to topic $includingRootTopic")
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun unsubscribe(topic:          String,
                    cbUnsubscribe:  IMqttActionListener = defaultCbUnsubscribe) {
        try {
            val includingRootTopic = addRootTopic(topic)
            mqttClient.unsubscribe(includingRootTopic, null, cbUnsubscribe)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun publish(topic:      String,
                msg:        String,
                qos:        Int                 = 1,
                retained:   Boolean             = false,
                cbPublish:  IMqttActionListener = defaultCbPublish) {
        try {
            val message = MqttMessage()
            message.payload = msg.toByteArray()
            message.qos = qos
            message.isRetained = retained
            val includingRootTopic = addRootTopic(topic)
            mqttClient.publish(includingRootTopic, message, null, cbPublish)
            //Log.d(LOGTAG, "Sent message $msg on topic $includingRootTopic")
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun disconnect(cbDisconnect: IMqttActionListener = defaultCbDisconnect ) {
        try {
            Log.d(LOGTAG, "Trying to disconnect")

            // First notify the topic handlers, so they can prepare if necessary
            mTopicManager.onDisconnect()

            // Now perform the disconnect
            mqttClient.disconnect(null, cbDisconnect)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun getProperty(propertyKey: String) : Any? {
        return mProperties[propertyKey]
    }
}