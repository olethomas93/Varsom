package com.appkungen.skredvarsel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Reschedules notifications after device reboot
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isReschedulingTrigger = action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!isReschedulingTrigger) return

        Log.d("BootReceiver", "Trigger: $action — rescheduling notifications")

        val prefs = context.getSharedPreferences(
            WidgetConstants.SHARED_PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val notificationsEnabled = prefs.getBoolean("notifications_enabled", false)
        if (notificationsEnabled) {
            val hour = prefs.getInt("notification_hour", 7)
            val minute = prefs.getInt("notification_minute", 0)
            NotificationScheduler.scheduleDaily(context, hour, minute)
            Log.d("BootReceiver", "Notifications rescheduled for $hour:$minute")
        }
    }
}