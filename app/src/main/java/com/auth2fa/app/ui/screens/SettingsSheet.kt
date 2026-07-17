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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    isDarkTheme: Boolean,
    biometricEnabled: Boolean,
    accountCount: Int,
    onToggleTheme: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
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

            SettingsItem(
                icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                title = if (isDarkTheme) "深色模式" else "浅色模式",
                trailing = {
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleTheme() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                },
                onClick = onToggleTheme
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
