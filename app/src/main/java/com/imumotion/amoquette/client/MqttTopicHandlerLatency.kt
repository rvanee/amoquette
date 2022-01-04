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

import com.imumotion.amoquette.*
import kotlin.math.sqrt

class MqttTopicHandlerLatency(manager: MqttTopicManager) :
    MqttTopicHandler(TOPIC_LATENCY, manager) {
    val mLatencyList: MutableList<Double> = mutableListOf()

    override fun onConnected() {
        mLatencyList.clear()
    }

    override fun processMessage(message: String?, topic: String) : Map<String, Any> {
        // Unpack message and calculate latency
        val map = getMapFromJson(message)
        if (map.isEmpty()) {
            throw Exception("Empty $TOPIC_LATENCY message received!")
        }

        val source = map[MESSAGE_FIELD_SOURCE] as String

        // Only process latencies that we generated ourselves
        if(source == MQTTCLIENT_ID) {
            val latency = map[MESSAGE_FIELD_LATENCY] as Double
            mLatencyList.add(latency)
        }

        // Create and send displayvalues message
        val statisticsMap = createHeader()
        calculateStatistics(statisticsMap)
        sendMessage(TOPIC_LATENCY_STATISTICS, statisticsMap)

        // Reuse statisticsMap as return map
        return statisticsMap
    }

    private fun calculateStatistics(map: MutableMap<String, Any>) {
        val n = mLatencyList.size
        val mean = mLatencyList.sum() / n

        var min = Double.POSITIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY
        val stddev = run {
            var sumsquareddiff = 0.0
            mLatencyList.forEach {
                val diff = it - mean
                sumsquareddiff += diff*diff
                min = if (it < min) it else min
                max = if (it > max) it else max
            }
            val variance = sumsquareddiff / n
            sqrt(variance)
        }

        map[LATENCY_MIN] = min.toLong()
        map[LATENCY_MAX] = max.toLong()
        map[LATENCY_MEAN] = mean
        map[LATENCY_STDDEV] = stddev
        map[LATENCY_NUMBER] = n
    }
}