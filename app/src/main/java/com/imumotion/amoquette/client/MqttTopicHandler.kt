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

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.imumotion.amoquette.MESSAGE_FIELD_ID
import com.imumotion.amoquette.MESSAGE_FIELD_SOURCE
import com.imumotion.amoquette.MESSAGE_FIELD_TIMESTAMP
import com.imumotion.amoquette.MQTTCLIENT_ID
import java.util.*

abstract class MqttTopicHandler(topic: String, manager: MqttTopicManager) {
    private val mTopic = topic
    protected val mManager = manager

    private val mapType = object : TypeToken<Map<String, Any>>() {}.getType()

    companion object {
        protected val gson = Gson()
    }

    fun getTopic(): String {
        return mTopic
    }

    open fun onConnected() {
        // Do nothing
    }

    open fun onDisconnect() {
        // Do nothing
    }

    abstract fun processMessage(message: String?, topic: String) : Map<String, Any>
    /* Implement processing of this message. The first step will often be to
       decode the json message to a map:
       val map = getMapFromJson(message)
       The function returns either a map or null.
     */

    protected fun getMapFromJson(message: String?) : Map<String, Any> {
        if(message != null) {
            return gson.fromJson(message, mapType)
        }
        return emptyMap()
    }

    protected fun getJsonFromMap(map: Map<String, Any>) : String {
        return gson.toJson(map, mapType)
    }

    protected fun getNewID() : String {
        // Create new unique, random ID
        return UUID.randomUUID().toString()
    }

    protected fun getCurrentTimestamp() : Long {
        return System.currentTimeMillis()
    }

    protected fun createHeader() : MutableMap<String, Any> {
        val map = HashMap<String, Any>()
        map.put(MESSAGE_FIELD_SOURCE, MQTTCLIENT_ID)
        map.put(MESSAGE_FIELD_ID, getNewID())
        map.put(MESSAGE_FIELD_TIMESTAMP, getCurrentTimestamp())
        return map
    }

    protected fun sendMessage(topic: String, map: Map<String, Any>) : Boolean {
        val message = getJsonFromMap(map)
        return mManager.sendMessage(topic, message)
    }
}
