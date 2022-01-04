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

import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class EditTextPreferenceHelper(preference: EditTextPreference?, isNumeric: Boolean = false) :
        Preference.SummaryProvider<EditTextPreference> {
        private val mSummary = preference?.summary.toString()

        init {
            if(isNumeric) { // Limit input characters to 0..9 if numeric preference
                preference?.setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_NUMBER
                }
            }
        }
        /**
         * Called whenever [.getSummary] is called on this preference.
         *
         * @param preference This preference
         * @return A CharSequence that will be displayed as the summary for this preference
         */
        override fun provideSummary(preference: EditTextPreference?): CharSequence {
            val value = if (TextUtils.isEmpty(preference?.text))
                "Not set" else preference?.text
            return mSummary + "\nCurrent value: " + value
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // Special cases
            // Host
            val hostPreference: EditTextPreference? = findPreference(BROKER_PROPERTY_HOST)
            hostPreference?.summaryProvider = EditTextPreferenceHelper(hostPreference)

            // Port
            val portPreference: EditTextPreference? = findPreference(BROKER_PROPERTY_PORT)
            portPreference?.summaryProvider =
                EditTextPreferenceHelper(portPreference, true)

            // Wake lock duration
            val wakelockPreference: EditTextPreference? =
                findPreference(BROKER_PROPERTY_WAKE_LOCK_DURATION)
            wakelockPreference?.summaryProvider =
                EditTextPreferenceHelper(wakelockPreference, true)

            // Message size
            val msgsizePreference: EditTextPreference? =
                findPreference(BROKER_PROPERTY_MESSAGE_SIZE)
            msgsizePreference?.summaryProvider =
                EditTextPreferenceHelper(msgsizePreference, true)

            // $SYS messages interval
            val sysPreference: EditTextPreference? =
                findPreference(BROKER_PROPERTY_SYSMSG_INTERVAL)
            sysPreference?.summaryProvider =
                EditTextPreferenceHelper(sysPreference, true)

            // Heartbeat interval
            val heartbeatPreference: EditTextPreference? =
                findPreference(BROKER_PROPERTY_HEARTBEAT_INTERVAL)
            heartbeatPreference?.summaryProvider =
                EditTextPreferenceHelper(heartbeatPreference, true)
        }
    }
}