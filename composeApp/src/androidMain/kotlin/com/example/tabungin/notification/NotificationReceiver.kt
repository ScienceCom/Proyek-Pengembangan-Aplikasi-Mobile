package com.example.tabungin.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SHOW_REMINDER -> {
                val namaUser = intent.getStringExtra(AlarmScheduler.EXTRA_USER_NAME) ?: ""
                NotificationHelper.showReminderNotification(context, namaUser)
            }
        }
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "com.example.tabungin.SHOW_REMINDER"
    }
}