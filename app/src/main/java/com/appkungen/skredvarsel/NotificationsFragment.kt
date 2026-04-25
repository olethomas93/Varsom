package com.appkungen.skredvarsel

import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.appkungen.varsomwidget.R

class NotificationsFragment : Fragment() {

    private lateinit var notificationSwitch: CompoundButton
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
        widgetPrefs = WidgetPreferences(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        loadSettings(view)
    }

    private fun setupViews(view: View) {
        notificationSwitch = view.findViewById(R.id.notification_switch)
        timePickerButton = view.findViewById(R.id.time_picker_button)
        testNotificationButton = view.findViewById(R.id.test_notification_button)
        statusText = view.findViewById(R.id.status_text)

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

    private fun loadSettings(view: View) {
        val prefs = requireContext().getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
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
                    requireContext(),
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
            enableNotifications()
        }
    }

    private fun enableNotifications() {
        val prefs = requireContext().getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
        val hour = prefs.getInt("notification_hour", 7)
        val minute = prefs.getInt("notification_minute", 0)

        prefs.edit().apply {
            putBoolean("notifications_enabled", true)
            apply()
        }

        NotificationScheduler.scheduleDaily(requireContext(), hour, minute)
        updateStatusText()
        Toast.makeText(requireContext(), "Daglige varsler aktivert", Toast.LENGTH_SHORT).show()
    }

    private fun disableNotifications() {
        val prefs = requireContext().getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("notifications_enabled", false)
            apply()
        }

        NotificationScheduler.cancelDaily(requireContext())
        updateStatusText()
        Toast.makeText(requireContext(), "Daglige varsler deaktivert", Toast.LENGTH_SHORT).show()
    }

    private fun showTimePicker() {
        val prefs = requireContext().getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
        val currentHour = prefs.getInt("notification_hour", 7)
        val currentMinute = prefs.getInt("notification_minute", 0)

        val timePicker = android.app.TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                saveNotificationTime(hourOfDay, minute)
            },
            currentHour,
            currentMinute,
            true
        )

        timePicker.setTitle("Velg varslingstidspunkt")
        timePicker.show()
    }

    private fun saveNotificationTime(hour: Int, minute: Int) {
        val prefs = requireContext().getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putInt("notification_hour", hour)
            putInt("notification_minute", minute)
            apply()
        }

        updateTimeButton(hour, minute)

        if (notificationSwitch.isChecked) {
            NotificationScheduler.scheduleDaily(requireContext(), hour, minute)
            Toast.makeText(
                requireContext(),
                "Varslingstidspunkt oppdatert til ${String.format("%02d:%02d", hour, minute)}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateTimeButton(hour: Int, minute: Int) {
        timePickerButton.text = "Tidspunkt: ${String.format("%02d:%02d", hour, minute)}"
    }

    private fun updateStatusText() {
        val prefs = requireContext().getSharedPreferences(WidgetConstants.SHARED_PREFS_NAME, MODE_PRIVATE)
        val enabled = prefs.getBoolean("notifications_enabled", false)

        statusText.text = if (enabled) {
            val hour = prefs.getInt("notification_hour", 7)
            val minute = prefs.getInt("notification_minute", 0)
            "Daglig varsel kl. ${String.format("%02d:%02d", hour, minute)}"
        } else {
            "Varsler er deaktivert"
        }
    }

    private fun sendTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(requireContext(), "Varslingstillatelse kreves", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val httpClient = HttpClient()
        val widgetPrefs = WidgetPreferences(requireContext())

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
            activity?.runOnUiThread {
                if (error != null) {
                    Toast.makeText(requireContext(), "Kunne ikke laste varsel", Toast.LENGTH_SHORT).show()
                } else {
                    response?.let {
                        val list = parseJsonToArrayList(it)
                        if (list != null && list.size >= 2) {
                            val today = list[1]
                            NotificationScheduler.sendNotification(
                                requireContext(),
                                today.RegionName,
                                today.DangerLevel,
                                today.MainText
                            )
                            Toast.makeText(requireContext(), "Testvarsel sendt", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Varslingstillatelse")
            .setMessage("Denne appen trenger tillatelse til å sende varsler for å gi deg daglige skredfarevarsel.")
            .setPositiveButton("Gi tillatelse") { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("Avbryt") { dialog, _ ->
                notificationSwitch.isChecked = false
                dialog.dismiss()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Tillatelse Nektet")
            .setMessage("Varslingstillatelse er nødvendig for daglige varsel. Du kan aktivere det i app-innstillinger.")
            .setPositiveButton("Åpne Innstillinger") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                startActivity(intent)
            }
            .setNegativeButton("Avbryt") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
