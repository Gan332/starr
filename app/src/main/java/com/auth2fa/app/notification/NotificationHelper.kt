package com.auth2fa.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.auth2fa.app.MainActivity
import com.auth2fa.app.R
import com.auth2fa.app.data.Account
import com.auth2fa.app.totp.TOTPGenerator

/**
 * Manages showing TOTP codes in the notification bar.
 * Shows a persistent notification with all active accounts and their current codes.
 * Each code has a "copy" action button for one-tap clipboard copy.
 */
object NotificationHelper {

    const val CHANNEL_ID = "totp_codes"
    const val CHANNEL_NAME = "验证码通知"
    const val CHANNEL_DESC = "在通知栏显示当前 TOTP 验证码"
    const val NOTIFICATION_ID = 1001

    // Action constants for BroadcastReceiver
    const val ACTION_COPY_CODE = "com.auth2fa.app.ACTION_COPY_CODE"
    const val EXTRA_ACCOUNT_ID = "account_id"
    const val EXTRA_ACCOUNT_ISSUER = "account_issuer"
    const val EXTRA_ACCOUNT_CODE = "account_code"

    private const val MAX_VISIBLE_ACCOUNTS = 5

    /**
     * Create the notification channel (required for Android 8+).
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW  // Low = no sound, just shows in drawer
        ).apply {
            description = CHANNEL_DESC
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Build and show the TOTP notification with all active accounts.
     * @param context Application context
     * @param accounts List of active accounts
     */
    fun show(context: Context, accounts: List<Account>) {
        createChannel(context)

        val now = System.currentTimeMillis() / 1000
        val visibleAccounts = accounts.take(MAX_VISIBLE_ACCOUNTS)

        // Build the notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🔐 Authenticator")
            .setContentText("${accounts.size} 个账户活跃")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setGroup(GROUP_KEY)

        // Tap notification to open app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(openPendingIntent)

        if (accounts.isEmpty()) {
            builder.setContentText("还没有添加账户")
        } else {
            // Show code for the first account as the main content
            try {
                val first = visibleAccounts[0]
                val code = TOTPGenerator.generate(first.secret, first.digits, first.period, now)
                val remaining = TOTPGenerator.getTimeRemaining(first.period, now)
                builder.setContentText(
                    "${first.issuer}: ${formatCode(code)} · ${remaining}s"
                )
            } catch (_: Exception) { }
        }

        // Add expanded style with all accounts
        if (visibleAccounts.size > 1) {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("🔐 Authenticator · ${accounts.size} 个账户")

            for (account in visibleAccounts) {
                try {
                    val code = TOTPGenerator.generate(
                        account.secret, account.digits, account.period, now
                    )
                    val remaining = TOTPGenerator.getTimeRemaining(account.period, now)
                    val line = "${account.issuer}:  ${formatCode(code)}  ·  ${remaining}s"
                    inboxStyle.addLine(line)

                    // Add copy action for each code
                    val copyIntent = Intent(context, NotificationReceiver::class.java).apply {
                        action = ACTION_COPY_CODE
                        putExtra(EXTRA_ACCOUNT_ID, account.id)
                        putExtra(EXTRA_ACCOUNT_ISSUER, account.issuer)
                        putExtra(EXTRA_ACCOUNT_CODE, code)
                    }
                    val copyPendingIntent = PendingIntent.getBroadcast(
                        context,
                        account.id.toInt(),
                        copyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(
                        0,
                        "复制 ${account.issuer}",
                        copyPendingIntent
                    )
                } catch (_: Exception) { }
            }

            // Show remaining accounts count if there are more
            if (accounts.size > MAX_VISIBLE_ACCOUNTS) {
                inboxStyle.setSummaryText("还有 ${accounts.size - MAX_VISIBLE_ACCOUNTS} 个账户")
            }

            builder.setStyle(inboxStyle)
        } else if (accounts.size == 1) {
            // Single account: add a copy button
            try {
                val account = visibleAccounts[0]
                val code = TOTPGenerator.generate(
                    account.secret, account.digits, account.period, now
                )
                val copyIntent = Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_COPY_CODE
                    putExtra(EXTRA_ACCOUNT_ID, account.id)
                    putExtra(EXTRA_ACCOUNT_ISSUER, account.issuer)
                    putExtra(EXTRA_ACCOUNT_CODE, code)
                }
                val copyPendingIntent = PendingIntent.getBroadcast(
                    context,
                    account.id.toInt(),
                    copyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "复制验证码", copyPendingIntent)
            } catch (_: Exception) { }
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Cancel the TOTP notification.
     */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Copy a code to the clipboard.
     */
    fun copyCodeToClipboard(context: Context, issuer: String, code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("2FA Code", code))

        // Show a toast
        android.widget.Toast.makeText(
            context,
            "已复制 $issuer 验证码",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Format a code with a space in the middle (e.g., "123 456").
     */
    private fun formatCode(code: String): String {
        return if (code.length >= 6) {
            "${code.substring(0, 3)} ${code.substring(3)}"
        } else {
            code
        }
    }

    private const val GROUP_KEY = "totp_codes_group"
}
