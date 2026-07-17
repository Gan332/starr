package com.auth2fa.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auth2fa.app.data.AccountRepository
import com.auth2fa.app.ui.theme.Auth2FATheme
import com.auth2fa.app.viewmodel.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class WidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED initially
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Load selected accounts
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        val selectedIds = prefs.getStringSet("widget_${appWidgetId}_accounts", emptySet())?.mapNotNull {
            it.toLongOrNull()
        }?.toSet() ?: emptySet()

        val repository = AccountRepository.getInstance(this)
        val accounts = runBlocking { repository.getAllList() }

        setContent {
            Auth2FATheme(themeMode = ThemeMode.DARK) {
                var selected by remember { mutableStateOf(selectedIds) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("选择 Widget 账户", fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    bottomBar = {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { finish() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("取消") }
                                Button(
                                    onClick = {
                                        prefs.edit().putStringSet(
                                            "widget_${appWidgetId}_accounts",
                                            selected.map { it.toString() }.toSet()
                                        ).apply()

                                        val appWidgetManager = AppWidgetManager.getInstance(this@WidgetConfigureActivity)
                                        TOTPWidgetProvider.updateAppWidget(this@WidgetConfigureActivity, appWidgetManager, appWidgetId)

                                        val result = Intent()
                                        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                        setResult(RESULT_OK, result)
                                        finish()
                                    },
                                    enabled = selected.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("确认") }
                            }
                        }
                    }
                ) { padding ->
                    if (accounts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("还没有账户", fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                Text(
                                    "选择要在 Widget 上显示的账户（最多 5 个）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(accounts.take(10), key = { it.id }) { account ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (account.id in selected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(account.issuer, fontWeight = FontWeight.Medium,
                                                style = MaterialTheme.typography.bodyLarge)
                                            if (account.name.isNotEmpty()) {
                                                Text(account.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Checkbox(
                                            checked = account.id in selected,
                                            onCheckedChange = { checked ->
                                                selected = if (checked) {
                                                    if (selected.size < 5) selected + account.id
                                                    else selected
                                                } else {
                                                    selected - account.id
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}