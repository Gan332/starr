package com.auth2fa.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.auth2fa.app.ui.theme.*
import com.auth2fa.app.viewmodel.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    themeMode: ThemeMode,
    biometricEnabled: Boolean,
    autoLockEnabled: Boolean,
    pinEnabled: Boolean,
    isMaterialYou: Boolean,
    accountCount: Int,
    timeCorrection: Long,
    onCycleTheme: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleAutoLock: (Boolean) -> Unit,
    onToggleMaterialYou: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onEncryptedExport: (String) -> Unit,
    onEncryptedImport: () -> Unit,
    onTrashClick: () -> Unit,
    onSetPin: (String) -> Boolean,
    onVerifyPin: (String) -> Boolean,
    onDisablePin: () -> Unit,
    onSetTimeCorrection: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showEncryptExportDialog by remember { mutableStateOf(false) }
    var showEncryptImportDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var encryptPassword by remember { mutableStateOf("") }
    var encryptError by remember { mutableStateOf("") }
    var showTimeCorrectionDialog by remember { mutableStateOf(false) }
    var timeCorrectionInput by remember { mutableStateOf(timeCorrection.toString()) }

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

            // Appearance
            Text(
                text = "外观",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Palette,
                title = "主题模式",
                subtitle = when (themeMode) {
                    ThemeMode.LIGHT -> "浅色"
                    ThemeMode.DARK -> "深色"
                    ThemeMode.SYSTEM -> "跟随系统"
                },
                onClick = onCycleTheme
            )

            if (isMaterialYou) {
                SettingsItem(
                    icon = Icons.Default.AutoAwesome,
                    title = "Material You 动态颜色",
                    subtitle = "已启用",
                    trailing = {
                        Switch(
                            checked = isMaterialYou,
                            onCheckedChange = { onToggleMaterialYou() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    },
                    onClick = onToggleMaterialYou
                )
            } else {
                SettingsItem(
                    icon = Icons.Default.AutoAwesome,
                    title = "Material You 动态颜色",
                    subtitle = "使用系统主题色（Android 12+）",
                    trailing = {
                        Switch(
                            checked = isMaterialYou,
                            onCheckedChange = { onToggleMaterialYou() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    },
                    onClick = onToggleMaterialYou
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Security
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
                title = "自动锁定",
                subtitle = "应用切换至后台时自动锁定",
                trailing = {
                    Switch(
                        checked = autoLockEnabled,
                        onCheckedChange = { onToggleAutoLock(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                },
                onClick = { onToggleAutoLock(!autoLockEnabled) }
            )

            SettingsItem(
                icon = Icons.Default.Pin,
                title = "PIN 码锁",
                subtitle = if (pinEnabled) "已设置" else "设置 PIN 码作为备选解锁方式",
                onClick = {
                    if (pinEnabled) {
                        showPinDialog = true
                    } else {
                        showPinSetup = true
                        pinInput = ""
                        pinConfirm = ""
                        pinError = ""
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Data
            Text(
                text = "数据",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Upload,
                title = "导出备份（未加密）",
                subtitle = "JSON 格式",
                onClick = onExport
            )

            SettingsItem(
                icon = Icons.Default.Lock,
                title = "导出加密备份",
                subtitle = "AES-256-GCM 加密",
                onClick = { showEncryptExportDialog = true }
            )

            SettingsItem(
                icon = Icons.Default.Download,
                title = "导入备份",
                subtitle = "支持未加密和加密 JSON",
                onClick = onImport
            )

            SettingsItem(
                icon = Icons.Default.Download,
                title = "导入加密备份",
                subtitle = "需要密码解密",
                onClick = { showEncryptImportDialog = true }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Maintenance
            Text(
                text = "维护",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "回收站",
                subtitle = "管理已删除的账户",
                onClick = onTrashClick
            )

            SettingsItem(
                icon = Icons.Default.Timer,
                title = "时间校正",
                subtitle = "当前偏移: ${timeCorrection}s（解决验证码不同步）",
                onClick = { showTimeCorrectionDialog = true }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // About
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
                subtitle = "v2.0.0 · 本地 TOTP/HOTP 验证器",
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

    // ---- PIN Setup Dialog ----
    if (showPinSetup) {
        AlertDialog(
            onDismissRequest = { showPinSetup = false },
            title = { Text("设置 PIN 码") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it; pinError = "" },
                        label = { Text("输入 4-6 位 PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        isError = pinError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { pinConfirm = it; pinError = "" },
                        label = { Text("确认 PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        isError = pinError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError.isNotEmpty()) {
                        Text(
                            text = pinError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pinInput.length < 4 || pinInput.length > 6 -> {
                            pinError = "PIN 必须为 4-6 位"
                        }
                        pinInput != pinConfirm -> {
                            pinError = "两次输入的 PIN 不一致"
                        }
                        else -> {
                            if (onSetPin(pinInput)) {
                                showPinSetup = false
                            } else {
                                pinError = "设置失败，请重试"
                            }
                        }
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetup = false }) { Text("取消") }
            }
        )
    }

    // ---- PIN Verify/Disable Dialog ----
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("PIN 码设置") },
            text = {
                Column {
                    Text("输入当前 PIN 以关闭 PIN 码锁")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it; pinError = "" },
                        label = { Text("当前 PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        singleLine = true,
                        isError = pinError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError.isNotEmpty()) {
                        Text(
                            text = pinError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onVerifyPin(pinInput)) {
                        onDisablePin()
                        showPinDialog = false
                        pinInput = ""
                    } else {
                        pinError = "PIN 错误"
                    }
                }) {
                    Text("关闭 PIN 码", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("取消") }
            }
        )
    }

    // ---- Encrypted Export Dialog ----
    if (showEncryptExportDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptExportDialog = false },
            title = { Text("加密导出") },
            text = {
                Column {
                    Text("设置导出密码")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = encryptPassword,
                        onValueChange = { encryptPassword = it; encryptError = "" },
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        isError = encryptError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (encryptError.isNotEmpty()) {
                        Text(text = encryptError, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (encryptPassword.length < 4) {
                        encryptError = "密码至少 4 位"
                    } else {
                        onEncryptedExport(encryptPassword)
                        showEncryptExportDialog = false
                        encryptPassword = ""
                    }
                }) { Text("导出") }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptExportDialog = false }) { Text("取消") }
            }
        )
    }

    // ---- Time Correction Dialog ----
    if (showTimeCorrectionDialog) {
        AlertDialog(
            onDismissRequest = { showTimeCorrectionDialog = false },
            title = { Text("时间校正") },
            text = {
                Column {
                    Text("如果验证码始终无法同步，可调整时间偏移（秒）。正数向前偏移，负数向后偏移。")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = timeCorrectionInput,
                        onValueChange = { timeCorrectionInput = it },
                        label = { Text("偏移量（秒）") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val offset = timeCorrectionInput.toLongOrNull()
                    if (offset != null) {
                        onSetTimeCorrection(offset)
                        showTimeCorrectionDialog = false
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimeCorrectionDialog = false }) { Text("取消") }
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
        if (trailing != null) trailing()
    }
}
