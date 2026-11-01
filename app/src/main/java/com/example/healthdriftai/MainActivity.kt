
package com.example.healthdriftai

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var sleepTimeText: TextView
    private lateinit var wakeTimeText: TextView
    private lateinit var monitoringStatusText: TextView
    private lateinit var lastSleepText: TextView
    private lateinit var averageSleepText: TextView
    private lateinit var sleepHistoryText: TextView

    private lateinit var setSleepButton: Button
    private lateinit var setWakeButton: Button
    private lateinit var startTrackingButton: Button

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            startSleepMonitoring()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()

        displaySavedSleepTime()
        displaySavedWakeTime()
        updateSleepSummary()

        setSleepButton.setOnClickListener {
            showSleepTimePicker()
        }

        setWakeButton.setOnClickListener {
            showWakeTimePicker()
        }

        startTrackingButton.setOnClickListener {
            checkPermissionsAndStartTracking()
        }

        scheduleSavedWakeUpAlarm()
        scheduleSavedSleepCheck()
    }

    private fun initializeViews() {
        sleepTimeText = findViewById(R.id.sleepTimeText)
        wakeTimeText = findViewById(R.id.wakeTimeText)
        monitoringStatusText = findViewById(R.id.monitoringStatusText)
        lastSleepText = findViewById(R.id.lastSleepText)
        averageSleepText = findViewById(R.id.averageSleepText)
        sleepHistoryText = findViewById(R.id.sleepHistoryText)

        setSleepButton = findViewById(R.id.setSleepButton)
        setWakeButton = findViewById(R.id.setWakeButton)
        startTrackingButton = findViewById(R.id.startTrackingButton)
    }

    // BEDTIME SETTINGS

    private fun showSleepTimePicker() {

        val hour = SleepPreferences.getSleepHour(this)
        val minute = SleepPreferences.getSleepMinute(this)

        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->

                SleepPreferences.saveSleepTime(
                    this,
                    selectedHour,
                    selectedMinute
                )

                displaySavedSleepTime()
                scheduleSavedSleepCheck()

                Toast.makeText(
                    this,
                    "Bedtime saved successfully!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            hour,
            minute,
            false
        ).show()
    }

    private fun displaySavedSleepTime() {

        val hour = SleepPreferences.getSleepHour(this)
        val minute = SleepPreferences.getSleepMinute(this)

        sleepTimeText.text =
            "Usual sleep time: ${formatTime(hour, minute)}"
    }

    private fun scheduleSavedSleepCheck() {

        val hour = SleepPreferences.getSleepHour(this)
        val minute = SleepPreferences.getSleepMinute(this)

        SleepAlarmScheduler.scheduleSleepCheck(
            this,
            hour,
            minute
        )
    }

    // WAKE-UP ALARM SETTINGS

    private fun showWakeTimePicker() {

        val hour = SleepPreferences.getWakeHour(this)
        val minute = SleepPreferences.getWakeMinute(this)

        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->

                SleepPreferences.saveWakeTime(
                    this,
                    selectedHour,
                    selectedMinute
                )

                displaySavedWakeTime()

                val scheduled = WakeUpAlarmScheduler.schedule(
                    this,
                    selectedHour,
                    selectedMinute
                )

                if (scheduled) {

                    Toast.makeText(
                        this,
                        "Daily wake-up alarm scheduled!",
                        Toast.LENGTH_LONG
                    ).show()

                } else {

                    Toast.makeText(
                        this,
                        "Please allow Alarms and reminders permission.",
                        Toast.LENGTH_LONG
                    ).show()

                    openExactAlarmSettings()
                }
            },
            hour,
            minute,
            false
        ).show()
    }

    private fun displaySavedWakeTime() {

        val hour = SleepPreferences.getWakeHour(this)
        val minute = SleepPreferences.getWakeMinute(this)

        wakeTimeText.text =
            "Usual wake-up time: ${formatTime(hour, minute)}"
    }

    private fun scheduleSavedWakeUpAlarm() {

        val hour = SleepPreferences.getWakeHour(this)
        val minute = SleepPreferences.getWakeMinute(this)

        val scheduled = WakeUpAlarmScheduler.schedule(
            this,
            hour,
            minute
        )

        if (!scheduled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            Toast.makeText(
                this,
                "Allow exact alarms in Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openExactAlarmSettings() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            )

            startActivity(intent)
        }
    }

    // SLEEP TRACKING PERMISSIONS

    private fun checkPermissionsAndStartTracking() {

        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        }

        if (permissionsNeeded.isNotEmpty()) {

            permissionLauncher.launch(
                permissionsNeeded.toTypedArray()
            )

        } else {

            startSleepMonitoring()
        }
    }

    private fun startSleepMonitoring() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            monitoringStatusText.text =
                "Activity permission is required."

            return
        }

        try {

            val serviceIntent = Intent(
                this,
                SleepMonitoringService::class.java
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            monitoringStatusText.text =
                "Sleep monitoring is active"

            Toast.makeText(
                this,
                "Sleep tracking started!",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            monitoringStatusText.text =
                "Unable to start sleep monitoring"

            Toast.makeText(
                this,
                "Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // SLEEP SUMMARY

    private fun updateSleepSummary() {

        // These fields are initialized here.
        // Connect your existing stored sleep-history data
        // to these TextViews when its format is confirmed.

        lastSleepText.text = "Last sleep duration: Not available"
        averageSleepText.text = "Average sleep duration: Not available"
        sleepHistoryText.text = "Your sleep history will appear here."
    }

    // TIME FORMATTING

    private fun formatTime(hour: Int, minute: Int): String {

        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }

        val period = if (hour < 12) "AM" else "PM"

        return String.format(
            Locale.getDefault(),
            "%02d:%02d %s",
            displayHour,
            minute,
            period
        )
    }

    override fun onResume() {
        super.onResume()

        if (::wakeTimeText.isInitialized) {
            displaySavedWakeTime()
            scheduleSavedWakeUpAlarm()
        }
    }
}
