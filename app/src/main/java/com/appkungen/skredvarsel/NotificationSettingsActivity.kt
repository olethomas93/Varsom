package com.appkungen.skredvarsel

import HttpClient
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.appkungen.varsomwidget.R

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var notificationSwitch: Switch
    private lateinit var timePickerButton: Button
    private lateinit var testNotificationButton: Button
    private lateinit var statusText: TextView
    private lateinit var widgetPrefs: WidgetPreferences

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableNotifications()
        } else {
            notificationSwitch.isChecked = false
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        widgetPrefs = WidgetPreferences(this)

        setupViews()
        loadSettings()
    }

    private fun setupViews() {
        notificationSwitch = findViewById(R.id.notification_switch)
        timePickerButton = findViewById(R.id.time_picker_button)
        testNotificationButton = findViewById(R.id.test_notification_button)
        statusText = findViewById(R.id.status_text)

        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestNotificationPermission()
            } else {
                disableNotifications()
            }
        }

        timePickerButton.setOnClickListener {
            showTimePicker()
        }

        testNotificationButton.setOnClickListener {
            sendTestNotification()
        }

        updateStatusText()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", false)
        val hour = prefs.getInt("notification_hour", 7)
        val minute = prefs.getInt("notification_minute", 0)

        notificationSwitch.isChecked = notificationsEnabled
        updateTimeButton(hour, minute)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    enableNotifications()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    showPermissionRationaleDialog()
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For Android 12 and below, no permission needed
            enableNotifications()
        }
    }

    private fun enableNotifications() {
        val prefs = getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
        val hour = prefs.getInt("notification_hour", 7)
        val minute = prefs.getInt("notification_minute", 0)

        prefs.edit().apply {
            putBoolean("notifications_enabled", true)
            apply()
        }

        NotificationScheduler.scheduleDaily(this, hour, minute)
        updateStatusText()
        Toast.makeText(this, "Daily notifications enabled", Toast.LENGTH_SHORT).show()
    }

    private fun disableNotifications() {
        val prefs = getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("notifications_enabled", false)
            apply()
        }

        NotificationScheduler.cancelDaily(this)
        updateStatusText()
        Toast.makeText(this, "Daily notifications disabled", Toast.LENGTH_SHORT).show()
    }

    private fun showTimePicker() {
        val prefs = getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
        val currentHour = prefs.getInt("notification_hour", 7)
        val currentMinute = prefs.getInt("notification_minute", 0)

        val timePicker = android.app.TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                saveNotificationTime(hourOfDay, minute)
            },
            currentHour,
            currentMinute,
            true // 24-hour format
        )

        timePicker.setTitle("Select notification time")
        timePicker.show()
    }

    private fun saveNotificationTime(hour: Int, minute: Int) {
        val prefs = getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putInt("notification_hour", hour)
            putInt("notification_minute", minute)
            apply()
        }

        updateTimeButton(hour, minute)

        // Reschedule if notifications are enabled
        if (notificationSwitch.isChecked) {
            NotificationScheduler.scheduleDaily(this, hour, minute)
            Toast.makeText(
                this,
                "Notification time updated to ${String.format("%02d:%02d", hour, minute)}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateTimeButton(hour: Int, minute: Int) {
        timePickerButton.text = "Time: ${String.format("%02d:%02d", hour, minute)}"
    }

    private fun updateStatusText() {
        val prefs = getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
        val enabled = prefs.getBoolean("notifications_enabled", false)

        statusText.text = if (enabled) {
            val hour = prefs.getInt("notification_hour", 7)
            val minute = prefs.getInt("notification_minute", 0)
            "Daily forecast at ${String.format("%02d:%02d", hour, minute)}"
        } else {
            "Notifications are disabled"
        }
    }

    private fun sendTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "Notification permission required", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Send test notification
        val httpClient = HttpClient()
        val widgetPrefs = WidgetPreferences(this)

        val lang = java.util.Locale.getDefault()
        val langCode = if (lang.toString().contains("nb")) {
            WidgetConstants.LANG_CODE_NORWEGIAN
        } else {
            WidgetConstants.LANG_CODE_ENGLISH
        }

        val (yesterdayDate, dayAfterTomorrowDate) = getYesterdayAndDayAfterTomorrow()

        val coord = widgetPrefs.fetchedCoord
        val selectedRegion = widgetPrefs.selectedRegion

        val url = when {
            coord != null -> {
                val obj = org.json.JSONObject(coord)
                "${WidgetConstants.API_BASE_URL}/AvalancheWarningByCoordinates/Simple/${obj.get("lat")}/${obj.get("lng")}/$langCode/$yesterdayDate/$dayAfterTomorrowDate"
            }
            selectedRegion != null -> {
                "${WidgetConstants.API_BASE_URL}/AvalancheWarningByRegion/Simple/$selectedRegion/$langCode/$yesterdayDate/$dayAfterTomorrowDate"
            }
            else -> {
                "${WidgetConstants.API_BASE_URL}/AvalancheWarningByRegion/Simple/${WidgetConstants.DEFAULT_REGION_ID}/$langCode/$yesterdayDate/$dayAfterTomorrowDate"
            }
        }

        httpClient.makeRequest(url) { response, error ->
            runOnUiThread {
                if (error != null) {
                    Toast.makeText(this, "Failed to load forecast", Toast.LENGTH_SHORT).show()
                } else {
                    response?.let {
                        val list = parseJsonToArrayList(it)
                        if (list != null && list.size >= 2) {
                            val today = list[1]
                            NotificationScheduler.sendNotification(
                                this,
                                today.RegionName,
                                today.DangerLevel,
                                today.MainText
                            )
                            Toast.makeText(this, "Test notification sent", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission")
            .setMessage("This app needs notification permission to send you daily avalanche forecasts.")
            .setPositiveButton("Grant") { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                notificationSwitch.isChecked = false
                dialog.dismiss()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("Notification permission is required for daily forecasts. You can enable it in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}