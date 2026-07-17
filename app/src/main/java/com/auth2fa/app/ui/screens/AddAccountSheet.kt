package com.auth2fa.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auth2fa.app.ui.components.QRScanner


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountSheet(
    onDismiss: () -> Unit,
    onAdd: (issuer: String, name: String, secret: String, accountType: String) -> Unit,
    onScannedUri: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current

    var issuer by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var isSecretValid by remember { mutableStateOf(true) }
    var accountType by remember { mutableStateOf("TOTP") }
    var digits by remember { mutableIntStateOf(6) }
    val isFormValid = issuer.isNotBlank() && secret.isNotBlank()

    var qrActive by remember { mutableStateOf(true) }

    val accountTypes = listOf("TOTP", "HOTP", "STEAM")
    var typeExpanded by remember { mutableStateOf(false) }

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
                text = "添加账户",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {},
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.clip(MaterialTheme.shapes.medium)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; qrActive = false },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Keyboard, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("手动输入")
                        }
                    },
                    selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; qrActive = true },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("扫码")
                        }
                    },
                    selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        value = issuer,
                        onValueChange = { issuer = it },
                        label = { Text("服务名称") },
                        placeholder = { Text("例如: GitHub, Google") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("账户名称（可选）") },
                        placeholder = { Text("例如: user@email.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Account type dropdown
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = accountType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("账户类型") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            accountTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                when (type) {
                                                    "TOTP" -> "TOTP（基于时间）"
                                                    "HOTP" -> "HOTP（基于计数器）"
                                                    "STEAM" -> "Steam（自定义格式）"
                                                    else -> type
                                                }
                                            )
                                            Text(
                                                when (type) {
                                                    "TOTP" -> "标准 30 秒周期验证码"
                                                    "HOTP" -> "基于计数器的验证码"
                                                    "STEAM" -> "5 位字母代码"
                                                    else -> ""
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        accountType = type
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = secret,
                        onValueChange = {
                            secret = it.uppercase().filter { c -> c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567=" }
                            isSecretValid = true
                        },
                        label = { Text("密钥") },
                        placeholder = { Text("输入 Base32 密钥") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = !isSecretValid,
                        supportingText = {
                            if (!isSecretValid) Text("密钥格式无效")
                        },
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                        }),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when (accountType) {
                            "STEAM" -> "Steam 密钥，由字母和数字组成（Base32 编码）"
                            "HOTP" -> "HOTP 密钥，每次使用后计数器 +1"
                            else -> "标准 TOTP 密钥，由字母和数字组成（Base32 编码）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (isFormValid) {
                                onAdd(issuer, name, secret, accountType)
                                onDismiss()
                            }
                        },
                        enabled = isFormValid,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("保存账户", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }

                1 -> {
                    QRScanner(
                        onCodeScanned = { uri -> onScannedUri(uri) },
                        isActive = qrActive,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "将二维码置于框内自动识别",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
