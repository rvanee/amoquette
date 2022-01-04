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

import android.os.*
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.imumotion.amoquette.BROKER_PROPERTY_SYSMSG_INTERVAL
import com.imumotion.amoquette.BuildConfig
import com.imumotion.amoquette.CONST_JSONSTRINGDATA
import java.lang.Runnable
import java.util.*

enum class HandlerAction {
    UNDEFINED,
    START,
    STOP,
    SYS
}

class BrokerHandlerThread(name: String, jsonproperties: String?) :
    HandlerThread(name, Process.THREAD_PRIORITY_MORE_FAVORABLE) {
    private val LOGTAG = this.javaClass.name

    private val gson = Gson()
    val mProperties = jsonproperties
    var sysmsginterval = 0L

    var mHandler: MqttHandler? = null
    var mBroker: MoquetteWrapper? = null

    // Start the broker after looper is prepared
    override fun onLooperPrepared() {
        super.onLooperPrepared()

        mHandler = MqttHandler(looper)

        // Create the broker
        val createBroker: Runnable = Runnable {
            // Create the broker object. It will run in the Handler's thread
            mBroker = MoquetteWrapper()
        }
        mHandler?.post(createBroker)

        // Now send start command to handler
        putMessage(HandlerAction.START, mProperties)
    }

    fun stopBroker() {
        putMessage(HandlerAction.STOP)
    }

    private fun putMessage(action: HandlerAction, jsonstring: String? = null) {
        val msg = Message.obtain(mHandler, action.ordinal)

        if (jsonstring != null) {
            val bundle = Bundle()
            bundle.putString(CONST_JSONSTRINGDATA, jsonstring)
            msg.data = bundle
        }
        msg.sendToTarget()
    }

    internal fun putMessageDelayed(action: HandlerAction, delayMillis: Long) {
        val msg = Message.obtain(mHandler, action.ordinal)
        mHandler?.sendMessageDelayed(msg, delayMillis)
    }

    override fun quitSafely(): Boolean {
        if(mBroker?.isStarted() == true) {
            //Log.d(LOGTAG, "Stopping broker from quitSafely()")
            mBroker?.stop()
        }

        //Log.d(LOGTAG,"Stopping HandlerThread through quitSafely()")
        return super.quitSafely()
    }

    // Handle broker start and stop messages
    inner class MqttHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                HandlerAction.START.ordinal -> {
                    // Extract parameters
                    val jsonparameterstring = msg.data?.getString(CONST_JSONSTRINGDATA)
                    val properties: Properties =
                        gson.fromJson(jsonparameterstring, object : TypeToken<Properties>() {}.type)

                    if(BuildConfig.DEBUG) {
                        Log.d(LOGTAG, "Starting broker with parameters " + jsonparameterstring)
                    }
                    mBroker?.start(properties)

                    // $SYS messages are produced at sysmsginterval [s] intervals.
                    // A value of 0 means they are disabled.
                    sysmsginterval =
                        (properties[BROKER_PROPERTY_SYSMSG_INTERVAL] as String).toLong()
                    if(sysmsginterval > 0L) {
                        putMessageDelayed(HandlerAction.SYS, sysmsginterval*1000L)
                    }
                }
                HandlerAction.SYS.ordinal -> {
                    if(mBroker?.isStarted() == true) {
                        mBroker?.generateSYSmessages()
                        putMessageDelayed(HandlerAction.SYS, sysmsginterval * 1000L)
                    }
                }
                HandlerAction.STOP.ordinal -> {
                    quitSafely()
                }
                else -> {
                    Log.e(LOGTAG,"Unknown message type received!")
                }
            }
        }
    }
}