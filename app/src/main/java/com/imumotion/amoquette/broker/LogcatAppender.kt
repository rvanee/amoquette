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

import android.util.Log
import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.Level
import org.apache.log4j.spi.LoggingEvent

/**
 * LogcatAppender converts log4j events to Android LogCat calls
 *
 * @author Rene van Ee
 */
class LogcatAppender(name: String) : AppenderSkeleton() {
    init {
        this.name = name
    }

    /**
     * This method is called by the [AppenderSkeleton.doAppend]
     * method.
     *
     *
     * If the output stream exists and is writable then write a log
     * statement to the output stream. Otherwise, write a single warning
     * message to `System.err`.
     *
     *
     * The format of the output will depend on this appender's
     * layout.
     *
     */
    public override fun append(event: LoggingEvent) {
        val message = event.renderedMessage
        val name = event.loggerName
        if (event.getLevel() === Level.DEBUG) {
            Log.d(name, message)
        } else if (event.getLevel() === Level.ERROR || event.getLevel() === Level.FATAL) {
            Log.e(name, message)
        } else if (event.getLevel() === Level.INFO) {
            Log.i(name, message)
        } else if (event.getLevel() === Level.WARN) {
            Log.w(name, message)
        } else {
            Log.d(name, message)
        }
    }

    /**
     * Release any resources allocated within the appender such as file
     * handles, network connections, etc.
     *
     *
     * It is a programming error to append to a closed appender.
     *
     * @since 0.8.4
     */
    override fun close() {
        // Do nothing
    }

    /**
     * Configurators call this method to determine if the appender
     * requires a layout. If this method returns `true`,
     * meaning that layout is required, then the configurator will
     * configure an layout using the configuration information at its
     * disposal.  If this method returns `false`, meaning that
     * a layout is not required, then layout configuration will be
     * skipped even if there is available layout configuration
     * information at the disposal of the configurator..
     *
     *
     * In the rather exceptional case, where the appender
     * implementation admits a layout but can also work without it, then
     * the appender should return `true`.
     *
     * @since 0.8.4
     */
    override fun requiresLayout(): Boolean {
        return false
    }
}
