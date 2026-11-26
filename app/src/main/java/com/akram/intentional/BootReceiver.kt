package com.akram.intentional

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            if (AppWatchService.hasPermissions(context)) {
                AppWatchService.start(context)
            }
        }
    }
}
