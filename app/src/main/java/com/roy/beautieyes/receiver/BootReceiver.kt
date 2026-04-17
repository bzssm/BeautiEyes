package com.roy.beautieyes.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.roy.beautieyes.service.SignalService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, SignalService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
