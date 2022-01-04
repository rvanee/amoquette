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

import com.imumotion.amoquette.TOPIC_SYS

class MqttTopicHandlerSys(manager: MqttTopicManager) :
    MqttTopicHandler(TOPIC_SYS, manager) {

    override fun processMessage(message: String?, topic: String) : Map<String, Any> {
        val returnmap = mutableMapOf<String, Any>()

        if(message!= null) {
            var numberLong: Long = Long.MAX_VALUE
            try {
                numberLong = message.toLong()
            } catch(e: NumberFormatException) {
                // do nothing
            }
            returnmap["value"] = if(message == numberLong.toString()) numberLong else message
        }

        return returnmap
    }
}
