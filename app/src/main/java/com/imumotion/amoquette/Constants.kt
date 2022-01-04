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

// Generic identifier for json string data, used in Intents
const val CONST_JSONSTRINGDATA = "jsonstringdata"

// Logcat logger name
const val LOGCAT_APPENDER = "logcatappender"

// Settings/properties
const val BROKER_PROPERTY_HOST = "host"
const val BROKER_PROPERTY_PORT = "port"
const val BROKER_PROPERTY_IMMEDIATE_BUFFER_FLUSH = "immediate_buffer_flush"
const val BROKER_PROPERTY_WAKE_LOCK_DURATION ="wakelockduration"
const val BROKER_PROPERTY_MESSAGE_SIZE = "netty.mqtt.message_size"
const val BROKER_PROPERTY_SYSMSG_INTERVAL = "sysmsginterval"
const val BROKER_PROPERTY_HEARTBEAT_INTERVAL = "heartbeatinterval"
const val BROKER_PROPERTY_ALLOW_ANONYMOUS ="allow_anonymous"

// Client name
const val MQTTCLIENT_ID = "AMoquetTester"

// Topics
const val TOPIC_TOPLEVEL = "moquette"
const val TOPIC_SYS = "\$SYS/#"
const val TOPIC_HEARTBEAT = "heartbeat"
const val TOPIC_LATENCY = "latency"
const val TOPIC_LATENCY_STATISTICS = "latencystatistics"

// Fields
const val MESSAGE_FIELD_SOURCE = "source"
const val MESSAGE_FIELD_ID = "id"
const val MESSAGE_FIELD_TIMESTAMP = "timestamp"
const val MESSAGE_FIELD_SENDER = "sender"  // Used by Heartbeat
const val MESSAGE_FIELD_LATENCY = "latency"  // Used by Heartbeat

// Heartbeat specific
const val HEARTBEAT_INTERVAL = "interval"
const val HEARTBEAT_NUMBER = "number"

// Latency specific
const val LATENCY_MIN = "min"
const val LATENCY_MAX = "max"
const val LATENCY_MEAN = "mean"
const val LATENCY_STDDEV = "stddev"
const val LATENCY_NUMBER = "number"
