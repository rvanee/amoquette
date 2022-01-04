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

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.imumotion.amoquette.*
import java.util.*

enum class Action {
    UNDEFINED,
    START,
    STOP
}

enum class ServiceState {
    UNDEFINED,
    CREATED,
    STARTED,
    STOPPED
}

class BrokerService : Service() {
    val LOGTAG = this.javaClass.name

    private val gson = Gson()

    private var state = ServiceState.UNDEFINED
    var brokerHandlerThread: BrokerHandlerThread? = null

    var mWakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? {
        Log.e(LOGTAG, "Some component wants to bind the service")
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(LOGTAG, "Service onStartCommand()")
        if (intent != null) {
            val action = intent.action
            val jsonproperties = intent.getStringExtra(CONST_JSONSTRINGDATA)
            Log.d(LOGTAG,"using an intent with action $action and json data $jsonproperties")
            when (action) {
                Action.START.name -> {
                    if(state != ServiceState.STARTED) {
                        Log.d(LOGTAG, "Starting the HandlerThread")
                        brokerHandlerThread =
                            BrokerHandlerThread("BrokerHandlerThread", jsonproperties)
                        brokerHandlerThread?.start()
                        state = ServiceState.STARTED

                        // Request wakelock if enabled
                        val properties: Properties =
                            gson.fromJson(jsonproperties, object : TypeToken<Properties>() {}.type)
                        val wakelock_duration =
                            (properties[BROKER_PROPERTY_WAKE_LOCK_DURATION] as String).toLong() *
                                    60 * 60 * 1000L  // Wake lock duration specified in hours
                        if (wakelock_duration > 0) {
                            Log.d(LOGTAG,
                                "Acquiring wake lock with duration $wakelock_duration ms")
                            mWakeLock =
                                (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                                    newWakeLock(
                                        PowerManager.PARTIAL_WAKE_LOCK,
                                        "AMoquette::keepalive"
                                    ).apply {
                                        acquire(wakelock_duration)
                                    }
                                }
                        }
                    }
                }
                Action.STOP.name -> {
                    if(state == ServiceState.STARTED) {
                        Log.d(LOGTAG, "Stopping the HandlerThread")
                        brokerHandlerThread?.stopBroker()
                        state = ServiceState.STOPPED

                        // Release wakelock
                        mWakeLock?.release()
                    }
                    Log.d(LOGTAG,"Stopping the service")
                    stopForeground(true)
                    stopSelf()
                }
                else -> Log.e(LOGTAG,"This should never happen. No action in the received intent")
            }
        } else {
            Log.d(LOGTAG,"with a null intent. It has probably been restarted by the system.")
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        Log.i(LOGTAG, "Service onCreate()")
        super.onCreate()

        if(state == ServiceState.UNDEFINED) {
            Log.i(LOGTAG, "Creating foreground service")
            val notification = createNotification()
            startForeground(1, notification)

            state = ServiceState.CREATED
        }
    }

    override fun onDestroy() {
        Log.i(LOGTAG, "Service onDestroy()")
        super.onDestroy()
        Log.i(LOGTAG, "Foreground service destroyed")
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "MQTT BROKER SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Mqtt Broker Service notifications channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).let {
                it.description = "Mqtt Broker Service channel"
                it.enableLights(true)
                it.lightColor = Color.BLUE
                //it.enableVibration(true)
                //it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val builder: Notification.Builder = Notification.Builder(
            this,
            notificationChannelId
        )

        return builder
            .setContentTitle("Moquette MQTT broker active")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_mqtt_smallicon)
            .build()
    }
}