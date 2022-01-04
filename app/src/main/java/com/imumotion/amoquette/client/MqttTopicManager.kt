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

class MqttTopicManager(mqttClient: MqttClient) {
    private val LOGTAG: String = this.javaClass.name

    private val mMqttClient = mqttClient

    private val mTopicHandlers = mutableMapOf<String, MqttTopicHandler>()
    private val mTopicRegexs = mutableMapOf<String, Regex>()

    init {
        // This is where the various handlers should be added
        addMqttTopicHandler(MqttTopicHandlerHeartbeat(this))
        addMqttTopicHandler(MqttTopicHandlerLatency(this))
        addMqttTopicHandler(MqttTopicHandlerSys(this))
    }

    fun getAllTopics(): Set<String> {
        return mTopicHandlers.keys.toSortedSet()
    }

    private fun addMqttTopicHandler(topicHandler: MqttTopicHandler) {
        val topic = topicHandler.getTopic()
        if(topic !in mTopicHandlers) {
            mTopicHandlers[topic] = topicHandler
            Log.d(LOGTAG,"MqttTopicHandler for $topic added")

            // The regex below makes sure that topics with a structure like
            // root/+/mytopic and root/# are recognized, as well as $SYS topics.
            mTopicRegexs[topic] = topic.replace("\$", "\\$").
                                    replace("+", "[^/]+").
                                    replace("#", ".+").
                                    toRegex()
        } else {
            throw IllegalArgumentException("Duplicate topic")
        }
    }

    fun onConnected() {
        mTopicHandlers.values.forEach { it.onConnected() }
    }

    fun onDisconnect() {
        mTopicHandlers.values.forEach { it.onDisconnect() }
    }

    fun onMessageReceived(topic: String, message: String?) : Map<String, Map<String, Any>> {
        val returnmap = mutableMapOf<String, Map<String, Any>>()

        if(topic in mTopicHandlers) {
            returnmap[topic] = mTopicHandlers[topic]!!.processMessage(message, topic)
        } else {
            mTopicHandlers.keys.forEach {
                // Use Regex associated with this subscription topic to test if
                // the provided topic matches
                if(mTopicRegexs[it]!!.find(topic) != null) {
                    returnmap[topic] = mTopicHandlers[it]!!.processMessage(message, topic)
                }
            }
            if(returnmap.isEmpty()) {
                Log.e(LOGTAG, "Topic $topic unknown")
            }
        }
        return returnmap
    }

    fun sendMessage(topic: String, message: String) : Boolean {
        val connected = mMqttClient.isConnected()
        if(connected) {
            mMqttClient.publish(topic, message)
        }
        return connected
    }

    fun getProperty(propertyKey: String) : Any? {
        return mMqttClient.getProperty(propertyKey)
    }
}