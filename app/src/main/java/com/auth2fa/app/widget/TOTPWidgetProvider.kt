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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove("widget_${appWidgetId}_accounts")
        }
        editor.apply()
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
                val allAccounts = runBlocking { repository.getAllList() }
                val now = System.currentTimeMillis() / 1000

                // Load selected accounts for this widget
                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                val selectedIds = prefs.getStringSet("widget_${appWidgetId}_accounts", null)?.mapNotNull {
                    it.toLongOrNull()
                }?.toSet()

                // Filter accounts based on selection, or show first 5 if no selection
                val filteredAccounts = if (selectedIds != null && selectedIds.isNotEmpty()) {
                    allAccounts.filter { it.id in selectedIds }.take(5)
                } else {
                    allAccounts.take(5)
                }

                if (filteredAccounts.isNotEmpty()) {
                    val codeText = StringBuilder()

                    for ((index, account) in filteredAccounts.withIndex()) {
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
                        if (index < filteredAccounts.lastIndex) {
                            codeText.append("\n")
                        }
                    }

                    if (allAccounts.size > filteredAccounts.size) {
                        codeText.append("\n+${allAccounts.size - filteredAccounts.size} more...")
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
