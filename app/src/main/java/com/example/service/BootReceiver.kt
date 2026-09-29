package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.repository.OmniRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("BootReceiver", "Boot completed or app updated. Starting Frank Voice Activation Service...")
            VoiceActivationService.start(context)

            // Reschedule active alarms
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val repo = OmniRepository(db)
                    val scheduler = SuperAdminReminderScheduler(context, repo)
                    scheduler.rescheduleAllPendingAlarms()
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule alarms on boot: ${e.message}")
                }
            }
        }
    }
}
