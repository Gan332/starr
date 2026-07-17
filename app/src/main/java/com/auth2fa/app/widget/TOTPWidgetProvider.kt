package com.auth2fa.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.auth2fa.app.MainActivity
import com.auth2fa.app.R
import com.auth2fa.app.data.AccountRepository
import com.auth2fa.app.totp.HOTPGenerator
import com.auth2fa.app.totp.SteamTOTPGenerator
import com.auth2fa.app.totp.TOTPGenerator
import kotlinx.coroutines.runBlocking

/**
 * App widget showing TOTP codes for all accounts.
 * Updates every 30 minutes (minimum Android allows) but also
 * updates when the user clicks refresh.
 */
class TOTPWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.totp_widget_layout)

            // Open app on title click
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

            // Build the code list
            try {
                val repository = AccountRepository.getInstance(context)
                val accounts = runBlocking { repository.getAllList() }
                val now = System.currentTimeMillis() / 1000

                if (accounts.isNotEmpty()) {
                    // Take first 5 accounts for widget display
                    val displayAccounts = accounts.take(5)
                    val codeText = StringBuilder()

                    for ((index, account) in displayAccounts.withIndex()) {
                        val code = try {
                            when (account.accountType) {
                                "STEAM" -> SteamTOTPGenerator.generate(account.secret, now)
                                "HOTP" -> HOTPGenerator.generate(account.secret, account.hotpCounter, account.digits)
                                else -> TOTPGenerator.generate(account.secret, account.digits, account.period, now)
                            }
                        } catch (e: Exception) {
                            "ERR"
                        }
                        codeText.append("${account.issuer}: $code")
                        if (index < displayAccounts.lastIndex) {
                            codeText.append("\n")
                        }
                    }

                    if (accounts.size > 5) {
                        codeText.append("\n+${accounts.size - 5} more...")
                    }

                    views.setTextViewText(R.id.widget_code_list_text, codeText.toString())
                    views.setViewVisibility(R.id.widget_code_list_text, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_empty_text, android.view.View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_code_list_text, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_empty_text, android.view.View.VISIBLE)
                }
            } catch (e: Exception) {
                views.setTextViewText(R.id.widget_code_list_text, "Error loading codes")
                views.setViewVisibility(R.id.widget_code_list_text, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_text, android.view.View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
