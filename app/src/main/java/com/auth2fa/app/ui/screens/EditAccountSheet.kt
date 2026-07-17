package com.auth2fa.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import com.auth2fa.app.data.Account
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAccountSheet(
    account: Account,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit
) {
    val focusManager = LocalFocusManager.current

    var issuer by remember { mutableStateOf(account.issuer) }
    var name by remember { mutableStateOf(account.name) }
    var customEmoji by remember { mutableStateOf(account.customEmoji) }
    var customColor by remember { mutableStateOf(account.customColor) }
    var category by remember { mutableStateOf(account.category) }
    var note by remember { mutableStateOf(account.note) }
    var tags by remember { mutableStateOf(account.tags) }

    val isFormValid = issuer.isNotBlank()
    val context = LocalContext.current

    fun buildOtpAuthUri(): String {
        val encodedIssuer = URLEncoder.encode(issuer.trim(), "UTF-8")
        val encodedName = if (name.isNotBlank()) URLEncoder.encode(name.trim(), "UTF-8") else ""
        val label = if (encodedName.isNotEmpty()) "$encodedIssuer:$encodedName" else encodedIssuer
        val sb = StringBuilder("otpauth://totp/$label?secret=${account.secret}")
        sb.append("&issuer=$encodedIssuer")
        if (account.digits != 6) sb.append("&digits=${account.digits}")
        if (account.period != 30) sb.append("&period=${account.period}")
        return sb.toString()
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
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Text(
                text = "编辑账户",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Issuer
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

            // Name
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

            // Category
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("分类（可选）") },
                placeholder = { Text("例如: 工作、个人、金融") },
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

            // Custom Emoji
            OutlinedTextField(
                value = customEmoji,
                onValueChange = { customEmoji = it },
                label = { Text("自定义图标 Emoji") },
                placeholder = { Text("留空则自动匹配") },
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

            // Note
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                placeholder = { Text("添加备注信息...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Tags
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("标签") },
                placeholder = { Text("用逗号分隔多个标签") },
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

            Spacer(modifier = Modifier.height(20.dp))

            // Share and Save buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val uri = buildOtpAuthUri()
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, uri)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "分享账户"))
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("分享", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }

                Button(
                    onClick = {
                        if (isFormValid) {
                            onSave(
                                account.copy(
                                    issuer = issuer.trim(),
                                    name = name.trim(),
                                    customEmoji = customEmoji.trim(),
                                    customColor = customColor,
                                    category = category.trim(),
                                    note = note.trim(),
                                    tags = tags.trim(),
                                )
                            )
                            onDismiss()
                        }
                    },
                    enabled = isFormValid,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("保存更改", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
