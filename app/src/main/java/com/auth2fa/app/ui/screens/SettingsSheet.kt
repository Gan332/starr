package com.auth2fa.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


import com.auth2fa.app.viewmodel.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    themeMode: ThemeMode,
    useMaterialYou: Boolean,
    biometricEnabled: Boolean,
    pinEnabled: Boolean,
    notificationEnabled: Boolean,
    accountCount: Int,
    onSetThemeMode: (ThemeMode) -> Unit,
    onToggleMaterialYou: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onTogglePin: (Boolean) -> Unit,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onTrashClick: () -> Unit,
    onCategoryAdminClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinRemoveDialog by remember { mutableStateOf(false) }
    var pinSetupValue by remember { mutableStateOf("") }
    var pinSetupError by remember { mutableStateOf("") }
    var isPinSet by remember { mutableStateOf(false) }
    var pinCheck by remember { mutableStateOf(false) }

    // Check if PIN is already set
    LaunchedEffect(Unit) {
        // We can't directly access ViewModel here, so we'll just rely on the pinEnabled parameter
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Appearance section
            Text(
                text = "外观",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Theme mode selector
            Text(
                text = "主题模式",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.LIGHT -> "☀️ 浅色"
                        ThemeMode.DARK -> "🌙 深色"
                        ThemeMode.SYSTEM -> "⚙️ 跟随系统"
                    }
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onSetThemeMode(mode) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Palette,
                title = "Material You 动态取色",
                subtitle = "使用系统主题色（Android 12+）",
                trailing = {
                    Switch(
                        checked = useMaterialYou,
                        onCheckedChange = { onToggleMaterialYou(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                },
                onClick = { onToggleMaterialYou(!useMaterialYou) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Security section
            Text(
                text = "安全",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Fingerprint,
                title = "生物识别锁",
                subtitle = "每次打开应用时需要验证身份",
                trailing = {
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { onToggleBiometric(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                },
                onClick = { onToggleBiometric(!biometricEnabled) }
            )

            SettingsItem(
                icon = Icons.Default.Lock,
                title = "PIN 锁",
                subtitle = if (pinEnabled) "使用 PIN 码解锁" else "设置 PIN 码保护账户",
                trailing = {
                    Switch(
                        checked = pinEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showPinSetupDialog = true
                            } else {
                                showPinRemoveDialog = true
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                },
                onClick = {
                    if (pinEnabled) showPinRemoveDialog = true else showPinSetupDialog = true
                }
            )

            SettingsItem(
                icon = Icons.Default.Notifications,
                title = "通知栏显示验证码",
                subtitle = "在通知栏快捷查看和复制验证码",
                trailing = {
                    Switch(
                        checked = notificationEnabled,
                        onCheckedChange = { onToggleNotification(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                },
                onClick = { onToggleNotification(!notificationEnabled) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Data section
            Text(
                text = "数据",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Upload,
                title = "导出备份",
                subtitle = "JSON 格式",
                onClick = onExport
            )

            SettingsItem(
                icon = Icons.Default.Download,
                title = "导入备份",
                subtitle = "JSON 格式",
                onClick = onImport
            )

            SettingsItem(
                icon = Icons.Default.Category,
                title = "分类管理",
                subtitle = "添加、编辑或删除分类",
                onClick = onCategoryAdminClick
            )

            SettingsItem(
                icon = Icons.Default.Delete,
                title = "回收站",
                subtitle = "查看已删除的账户",
                onClick = onTrashClick
            )

            Spacer(modifier = Modifier.height(20.dp))

            // About section
            Text(
                text = "关于",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Security,
                title = "Authenticator",
                subtitle = "v1.0.0 · 本地 TOTP 验证器",
                enabled = false
            )

            SettingsItem(
                icon = Icons.Default.AccountBalanceWallet,
                title = "$accountCount 个账户",
                subtitle = "所有数据仅存储在本地",
                enabled = false
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // PIN Setup Dialog
    if (showPinSetupDialog) {
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false; pinSetupValue = ""; pinSetupError = "" },
            title = { Text("设置 PIN 码", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("请输入 4~6 位数字 PIN 码用于解锁应用")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinSetupValue,
                        onValueChange = {
                            pinSetupValue = it.filter { c -> c.isDigit() }.take(6)
                            pinSetupError = ""
                        },
                        label = { Text("PIN 码") },
                        singleLine = true,
                        isError = pinSetupError.isNotEmpty(),
                        supportingText = if (pinSetupError.isNotEmpty()) {{ Text(pinSetupError, color = MaterialTheme.colorScheme.error) }} else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pinSetupValue.length < 4 -> pinSetupError = "PIN 码至少 4 位"
                        else -> {
                            onSetPin(pinSetupValue)
                            onTogglePin(true)
                            showPinSetupDialog = false
                            pinSetupValue = ""
                            pinSetupError = ""
                        }
                    }
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false; pinSetupValue = ""; pinSetupError = "" }) {
                    Text("取消")
                }
            }
        )
    }

    // PIN Remove Confirmation Dialog
    if (showPinRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showPinRemoveDialog = false },
            title = { Text("关闭 PIN 锁") },
            text = { Text("确定要关闭 PIN 码锁吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onRemovePin()
                    showPinRemoveDialog = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showPinRemoveDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.6f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled) { onClick() }
                else Modifier
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}
