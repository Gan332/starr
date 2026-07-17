package com.auth2fa.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auth2fa.app.data.Account
import com.auth2fa.app.viewmodel.CodeEntry
import com.auth2fa.app.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountCard(
    account: Account,
    codeEntry: CodeEntry?,
    isCopied: Boolean,
    isSelected: Boolean = false,
    isSelectMode: Boolean = false,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
    onIncrementHotp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accentColor = if (account.customColor != 0) Color(account.customColor)
        else getAccentColor(account.issuer)
    val emoji = account.customEmoji.ifEmpty { getIssuerEmoji(account.issuer) }

    val bgColor by animateColorAsState(
        targetValue = if (isCopied) accentColor.copy(alpha = 0.15f)
            else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300), label = "cardBg"
    )

    val isHotp = account.accountType == "HOTP"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .then(
                if (isSelectMode) Modifier.combinedClickable(
                    onClick = onToggleSelect,
                    onLongClick = onToggleSelect
                ) else Modifier.clickable { onCopy() }
            ),
        shape = RoundedCornerShape(RoundedLg),
        containerColor = bgColor,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Icon circle
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.issuer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (account.name.isNotEmpty()) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    if (account.category.isNotEmpty()) {
                        Text(
                            text = account.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor, maxLines = 1
                        )
                    }
                    if (isHotp) {
                        Text(
                            text = " · #${account.hotpCounter}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isSelectMode) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (account.isFavorite) Icons.Filled.Star else Icons.StarBorder,
                        contentDescription = if (account.isFavorite) "取消收藏" else "收藏",
                        tint = if (account.isFavorite) Color(0xFFFFC107)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Code display
            if (codeEntry != null && codeEntry.code != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = codeEntry.code,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (isHotp) FontFamily.Default else FontFamily.Monospace,
                        color = if (isCopied) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        letterSpacing = if (isHotp) 0.sp else 4.sp,
                        textAlign = TextAlign.End
                    )
                    if (isHotp) {
                        TextButton(
                            onClick = onIncrementHotp,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("+1", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        val progressColor = when {
                            codeEntry.remainingSeconds <= 5 -> Error
                            codeEntry.remainingSeconds <= 10 -> Warning
                            else -> Success
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(modifier = Modifier.width(40.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(modifier = Modifier.fillMaxHeight()
                                    .fillMaxWidth(fraction = 1f - codeEntry.progress)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(progressColor))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${codeEntry.remainingSeconds}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = progressColor, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else if (codeEntry != null) {
                Text("密钥无效", style = MaterialTheme.typography.bodySmall, color = Error)
            }
        }

        if (account.note.isNotEmpty()) {
            Text(
                text = account.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 82.dp, end = 16.dp, bottom = 12.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun getAccentColor(issuer: String): Color {
    val colors = listOf(
        Color(0xFF6C63FF), Color(0xFF3B82F6), Color(0xFF10B981),
        Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFEC4899),
        Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFF97316),
        Color(0xFF84CC16),
    )
    return colors[kotlin.math.abs(issuer.hashCode()) % colors.size]
}

fun getIssuerEmoji(issuer: String): String {
    return when {
        issuer.contains("github", ignoreCase = true) -> "\uD83D\uDC0D"
        issuer.contains("google", ignoreCase = true) -> "\uD83D\uDD0D"
        issuer.contains("microsoft", ignoreCase = true) ||
            issuer.contains("azure", ignoreCase = true) ||
            issuer.contains("office", ignoreCase = true) -> "\uD83D\uDDA5\uFE0F"
        issuer.contains("apple", ignoreCase = true) -> "\uD83C\uDF4E"
        issuer.contains("facebook", ignoreCase = true) ||
            issuer.contains("meta", ignoreCase = true) -> "\uD83D\uDC40"
        issuer.contains("twitter", ignoreCase = true) ||
            issuer.contains("x.com", ignoreCase = true) -> "\uD83D\uDCCD"
        issuer.contains("amazon", ignoreCase = true) -> "\uD83D\uDED2"
        issuer.contains("slack", ignoreCase = true) -> "\uD83D\uDCE3"
        issuer.contains("discord", ignoreCase = true) -> "\uD83C\uDFAE"
        issuer.contains("gitlab", ignoreCase = true) -> "\uD83E\uDD1A"
        issuer.contains("docker", ignoreCase = true) -> "\uD83D\uDC33"
        issuer.contains("steam", ignoreCase = true) -> "\uD83C\uDFAE"
        issuer.contains("aws", ignoreCase = true) -> "\u2601\uFE0F"
        issuer.contains("cloudflare", ignoreCase = true) -> "\u2601\uFE0F"
        issuer.contains("heroku", ignoreCase = true) -> "\uD83D\uDC3B"
        issuer.contains("digitalocean", ignoreCase = true) -> "\uD83C\uDF0A"
        issuer.contains("dropbox", ignoreCase = true) -> "\uD83D\uDCE6"
        issuer.contains("trello", ignoreCase = true) -> "\uD83D\uDCCB"
        issuer.contains("jira", ignoreCase = true) -> "\uD83D\uDEA8"
        issuer.contains("notion", ignoreCase = true) -> "\uD83D\uDCA1"
        issuer.contains("1password", ignoreCase = true) -> "\uD83D\uDD11"
        issuer.contains("lastpass", ignoreCase = true) -> "\uD83D\uDD12"
        issuer.contains("bitwarden", ignoreCase = true) -> "\uD83D\uDD10"
        issuer.contains("stripe", ignoreCase = true) -> "\uD83D\uDCB3"
        issuer.contains("paypal", ignoreCase = true) -> "\uD83D\uDCB5"
        issuer.contains("binance", ignoreCase = true) -> "\uD83D\uDCB1"
        issuer.contains("coinbase", ignoreCase = true) -> "\uD83E\uDE99"
        issuer.contains("instagram", ignoreCase = true) -> "\uD83D\uDCF7"
        issuer.contains("linkedin", ignoreCase = true) -> "\uD83D\uDCBC"
        issuer.contains("tiktok", ignoreCase = true) -> "\uD83C\uDFB6"
        issuer.contains("reddit", ignoreCase = true) -> "\uD83D\uDE80"
        issuer.contains("telegram", ignoreCase = true) -> "\u2709\uFE0F"
        issuer.contains("signal", ignoreCase = true) -> "\uD83D\uDD35"
        issuer.contains("uber", ignoreCase = true) -> "\uD83D\uDE95"
        issuer.contains("airbnb", ignoreCase = true) -> "\uD83C\uDFE0"
        issuer.contains("spotify", ignoreCase = true) -> "\uD83C\uDFB5"
        issuer.contains("netflix", ignoreCase = true) -> "\uD83C\uDFAC"
        issuer.contains("epic", ignoreCase = true) -> "\uD83C\uDFAE"
        issuer.contains("nintendo", ignoreCase = true) -> "\uD83C\uDFAE"
        issuer.contains("sony", ignoreCase = true) ||
            issuer.contains("playstation", ignoreCase = true) -> "\uD83C\uDFAE"
        issuer.contains("wordpress", ignoreCase = true) -> "\uD83C\uDF10"
        issuer.contains("shopify", ignoreCase = true) -> "\uD83D\uDED2"
        issuer.contains("patreon", ignoreCase = true) -> "\uD83C\uDF97\uFE0F"
        issuer.contains("samsung", ignoreCase = true) -> "\uD83D\uDCF1"
        issuer.contains("huawei", ignoreCase = true) -> "\uD83D\uDCF1"
        issuer.contains("xiaomi", ignoreCase = true) -> "\uD83D\uDCF1"
        issuer.contains("bank", ignoreCase = true) ||
            issuer.contains("chase", ignoreCase = true) ||
            issuer.contains("wells", ignoreCase = true) ||
            issuer.contains("capital", ignoreCase = true) -> "\uD83C\uDFE6"
        issuer.contains("school", ignoreCase = true) ||
            issuer.contains("edu", ignoreCase = true) ||
            issuer.contains("university", ignoreCase = true) -> "\uD83C\uDFEB"
        else -> "\uD83D\uDD12"
    }
}
