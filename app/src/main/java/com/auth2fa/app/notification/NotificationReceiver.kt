package com.auth2fa.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles notification action buttons (e.g., "copy code").
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationHelper.ACTION_COPY_CODE -> {
                val issuer = intent.getStringExtra(NotificationHelper.EXTRA_ACCOUNT_ISSUER) ?: ""
                val code = intent.getStringExtra(NotificationHelper.EXTRA_ACCOUNT_CODE) ?: ""
                if (code.isNotEmpty()) {
                    NotificationHelper.copyCodeToClipboard(context, issuer, code)
                }
            }
        }
    }
}
