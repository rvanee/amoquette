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

package com.imumotion.amoquette.broker

import com.imumotion.amoquette.LOGCAT_APPENDER
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import io.moquette.interception.AbstractInterceptHandler
import io.moquette.interception.InterceptHandler
import io.moquette.interception.messages.InterceptConnectMessage
import io.moquette.interception.messages.InterceptConnectionLostMessage
import io.moquette.interception.messages.InterceptDisconnectMessage
import io.netty.buffer.Unpooled
import io.netty.handler.codec.mqtt.MqttMessageBuilders
import io.netty.handler.codec.mqtt.MqttQoS
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import java.nio.charset.Charset
import java.util.*

class MoquetteWrapper() {
    private var logger: Logger? = null
    private val mqttBroker: Server
    private var started = false

    private var starttime = 0L

    init {
        // Setup log4j system by appending a Logcat Appender, if it has
        // not been appended before
        val rootlogger = LogManager.getRootLogger()
        if(rootlogger.getAppender(LOGCAT_APPENDER) == null) {
            rootlogger.addAppender(LogcatAppender(LOGCAT_APPENDER))
        }
        logger = LogManager.getLogger("MoquetteBroker")

        // Create broker object
        mqttBroker = Server()
    }

    inner class ConnectListener() : AbstractInterceptHandler() {
        /**
         * @return the identifier of this intercept handler.
         */
        override fun getID(): String {
            return "ConnectListener"
        }

        override fun onConnect(msg: InterceptConnectMessage?) {
            val username = msg?.username ?: ""
            val clientID = msg?.clientID ?: ""
            logger?.debug("Moquette MQTT broker connected to username@client " +
                    "\"$username\"@$clientID")
        }

        override fun onDisconnect(msg: InterceptDisconnectMessage?) {
            val username = msg?.username ?: ""
            val clientID = msg?.clientID ?: ""
            logger?.debug("Moquette MQTT broker disconnected " +
                    "from user@client \"$username\"@$clientID")
        }

        override fun onConnectionLost(msg: InterceptConnectionLostMessage?) {
            val username = msg?.username ?: ""
            val clientID = msg?.clientID ?: ""
            logger?.debug("Moquette MQTT broker lost connection " +
                    "from user@client \"$username\"@$clientID")
        }
    }

    fun start(properties: Properties) {
        val userHandlers: List<InterceptHandler?> = listOf(ConnectListener())

        logger?.debug("Starting broker with the following properties: " + properties.toString())
        mqttBroker.startServer(MemoryConfig(properties), userHandlers)

        starttime = Date().time
        started = true
    }

    fun stop() {
        logger?.debug("Stopping broker")
        mqttBroker.stopServer()
        started = false
    }

    fun isStarted() : Boolean {
        return started
    }

    fun generateSYSmessages() {
        val clientid = "AMoquette"
        val clientnr = mqttBroker.listConnectedClients().size
        val currenttime = Date().time
        val uptime = (currenttime - starttime) / 1000L  // In [s]

        logger?.debug("Sending \$SYS messages")

        internalPublish("\$SYS/broker/clients/connected",
            clientnr.toString(),
            clientid)
        internalPublish("\$SYS/broker/time",
            currenttime.toString(),
            clientid)
        internalPublish("\$SYS/broker/uptime",
            uptime.toString(),
            clientid)
    }

    private fun internalPublish(topic: String,
                                msg: String,
                                clientId: String,
                                retained: Boolean = true,
                                qos: MqttQoS = MqttQoS.AT_MOST_ONCE) {
        // Write msg to ByteBuf
        val msgByteBuf = Unpooled.copiedBuffer(msg, Charset.defaultCharset())

        val message = MqttMessageBuilders.publish()
            .topicName(topic)
            .retained(retained)
            .qos(qos)
            .payload(msgByteBuf)
            .build()

        mqttBroker.internalPublish(message, clientId)
    }

    private fun listConnectedClients(): Map<String, String> {
        val map  = mutableMapOf<String, String>()
        val clientcollection = mqttBroker.listConnectedClients()
        clientcollection.forEach {
            map.put(it.clientID, "${it.address}:${it.port}")
        }
        return map
    }
}