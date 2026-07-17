package com.auth2fa.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * A beautiful 2FA account card with large rounded corners.
 */
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
    modifier: Modifier = Modifier
) {
    val accentColor = if (account.customColor != 0) Color(account.customColor)
        else getAccentColor(account.issuer)
    val emoji = account.customEmoji.ifEmpty { getIssuerEmoji(account.issuer) }

    val bgColor by animateColorAsState(
        targetValue = if (isCopied) accentColor.copy(alpha = 0.15f)
            else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "cardBg"
    )


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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Select mode checkbox
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

            // Icon circle with emoji
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Account info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.issuer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (account.name.isNotEmpty()) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (account.category.isNotEmpty()) {
                    Text(
                        text = account.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Favorite icon
            if (!isSelectMode) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (account.isFavorite)
                            androidx.compose.material.icons.Icons.Filled.Star
                        else
                            androidx.compose.material.icons.Icons.Default.StarBorder,
                        contentDescription = if (account.isFavorite) "取消收藏" else "收藏",
                        tint = if (account.isFavorite)
                            Color(0xFFFFC107)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Code display
            if (codeEntry != null && codeEntry.code != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (account.isSteam) "STEAM" else codeEntry.code,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (account.isSteam) FontFamily.Default else FontFamily.Monospace,
                        color = if (isCopied) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        letterSpacing = if (account.isSteam) 0.sp else 4.sp,
                        textAlign = TextAlign.End
                    )
                    if (!account.isSteam) {
                        // Progress indicator
                        val progressColor = when {
                            codeEntry.remainingSeconds <= 5 -> Error
                            codeEntry.remainingSeconds <= 10 -> Warning
                            else -> Success
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = 1f - codeEntry.progress)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(progressColor)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${codeEntry.remainingSeconds}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = progressColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else if (codeEntry != null) {
                // Invalid secret
                Text(
                    text = "密钥无效",
                    style = MaterialTheme.typography.bodySmall,
                    color = Error
                )
            }
        }

        // Note display if exists
        if (account.note.isNotEmpty()) {
            Text(
                text = account.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 82.dp, end = 16.dp, bottom = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Deterministic accent color for an issuer name.
 */
fun getAccentColor(issuer: String): Color {
    val colors = listOf(
        Color(0xFF6C63FF), // Purple
        Color(0xFF3B82F6), // Blue
        Color(0xFF10B981), // Green
        Color(0xFFF59E0B), // Amber
        Color(0xFFEF4444), // Red
        Color(0xFFEC4899), // Pink
        Color(0xFF8B5CF6), // Violet
        Color(0xFF06B6D4), // Cyan
        Color(0xFFF97316), // Orange
        Color(0xFF84CC16), // Lime
    )
    val index = kotlin.math.abs(issuer.hashCode()) % colors.size
    return colors[index]
}

/**
 * Emoji for common issuers.
 */
fun getIssuerEmoji(issuer: String): String {
    return when {
        issuer.contains("github", ignoreCase = true) -> "\uD83D\uDC0D"     // GitHub snake
        issuer.contains("google", ignoreCase = true) -> "\uD83D\uDD0D"     // Google magnifying glass
        issuer.contains("microsoft", ignoreCase = true) ||
            issuer.contains("azure", ignoreCase = true) ||
            issuer.contains("office", ignoreCase = true) -> "\uD83D\uDDA5\uFE0F" // Windows
        issuer.contains("apple", ignoreCase = true) -> "\uD83C\uDF4E"      // Apple
        issuer.contains("facebook", ignoreCase = true) ||
            issuer.contains("meta", ignoreCase = true) -> "\uD83D\uDC40"   // Eyes (Meta)
        issuer.contains("twitter", ignoreCase = true) ||
            issuer.contains("x.com", ignoreCase = true) -> "\uD83D\uDCCD"   // Bird (X)
        issuer.contains("amazon", ignoreCase = true) -> "\uD83D\uDED2"     // Shopping
        issuer.contains("slack", ignoreCase = true) -> "\uD83D\uDCE3"      // Megaphone
        issuer.contains("discord", ignoreCase = true) -> "\uD83C\uDFAE"    // Gamepad
        issuer.contains("gitlab", ignoreCase = true) -> "\uD83E\uDD1A"     // GitLab fist
        issuer.contains("bitbucket", ignoreCase = true) -> "\uD83D\uDDB1\uFE0F" // Bitbucket
        issuer.contains("docker", ignoreCase = true) -> "\uD83D\uDC33"     // Docker whale
        issuer.contains("steam", ignoreCase = true) -> "\uD83C\uDFAE"      // Steam gamepad
        issuer.contains("aws", ignoreCase = true) ||
            issuer.contains("amazon", ignoreCase = true) -> "\u2601\uFE0F" // AWS cloud
        issuer.contains("cloudflare", ignoreCase = true) -> "\u2601\uFE0F" // Cloudflare
        issuer.contains("heroku", ignoreCase = true) -> "\uD83D\uDC3B"     // Heroku elephant
        issuer.contains("digitalocean", ignoreCase = true) -> "\uD83C\uDF0A" // DigitalOcean wave
        issuer.contains("linode", ignoreCase = true) -> "\uD83C\uDF31"     // Linode leaf
        issuer.contains("vultr", ignoreCase = true) -> "\uD83D\uDD25"      // Vultr fire
        issuer.contains("namecheap", ignoreCase = true) -> "\uD83C\uDF1F"  // Namecheap star
        issuer.contains("cloudflare", ignoreCase = true) -> "\u2601\uFE0F" // Cloud
        issuer.contains("dropbox", ignoreCase = true) -> "\uD83D\uDCE6"    // Dropbox box
        issuer.contains("trello", ignoreCase = true) -> "\uD83D\uDCCB"     // Trello board
        issuer.contains("jira", ignoreCase = true) -> "\uD83D\uDEA8"       // Jira (bug/issue)
        issuer.contains("confluence", ignoreCase = true) -> "\uD83D\uDCD6" // Confluence book
        issuer.contains("evernote", ignoreCase = true) -> "\uD83D\uDCDD"   // Evernote pencil
        issuer.contains("notion", ignoreCase = true) -> "\uD83D\uDCA1"     // Notion idea
        issuer.contains("1password", ignoreCase = true) -> "\uD83D\uDD11"  // 1Password key
        issuer.contains("lastpass", ignoreCase = true) -> "\uD83D\uDD12"   // LastPass lock
        issuer.contains("bitwarden", ignoreCase = true) -> "\uD83D\uDD10"  // Bitwarden shield
        issuer.contains("stripe", ignoreCase = true) -> "\uD83D\uDCB3"     // Stripe card
        issuer.contains("paypal", ignoreCase = true) -> "\uD83D\uDCB5"     // PayPal dollar
        issuer.contains("binance", ignoreCase = true) -> "\uD83D\uDCB1"    // Binance coin
        issuer.contains("coinbase", ignoreCase = true) -> "\uD83E\uDE99"   // Coinbase
        issuer.contains("instagram", ignoreCase = true) -> "\uD83D\uDCF7"  // Camera
        issuer.contains("linkedin", ignoreCase = true) -> "\uD83D\uDCBC"   // LinkedIn briefcase
        issuer.contains("tiktok", ignoreCase = true) -> "\uD83C\uDFB6"     // TikTok music
        issuer.contains("reddit", ignoreCase = true) -> "\uD83D\uDE80"     // Reddit rocket
        issuer.contains("whatsapp", ignoreCase = true) -> "\uD83D\uDCF1"   // WhatsApp phone
        issuer.contains("telegram", ignoreCase = true) -> "\u2709\uFE0F"   // Telegram mail
        issuer.contains("signal", ignoreCase = true) -> "\uD83D\uDD35"     // Signal blue circle
        issuer.contains("proton", ignoreCase = true) -> "\uD83D\uDD04"     // Proton arrows
        issuer.contains("uber", ignoreCase = true) -> "\uD83D\uDE95"       // Uber car
        issuer.contains("airbnb", ignoreCase = true) -> "\uD83C\uDFE0"     // Airbnb house
        issuer.contains("spotify", ignoreCase = true) -> "\uD83C\uDFB5"    // Spotify notes
        issuer.contains("netflix", ignoreCase = true) -> "\uD83C\uDFAC"    // Netflix clapper
        issuer.contains("twitch", ignoreCase = true) -> "\uD83C\uDF9F\uFE0F" // Twitch controller
        issuer.contains("epic", ignoreCase = true) -> "\uD83C\uDFAE"       // Epic games
        issuer.contains("ubisoft", ignoreCase = true) -> "\uD83C\uDFAE"    // Ubisoft
        issuer.contains("nintendo", ignoreCase = true) -> "\uD83C\uDFAE"   // Nintendo
        issuer.contains("sony", ignoreCase = true) ||
            issuer.contains("playstation", ignoreCase = true) -> "\uD83C\uDFAE" // Sony
        issuer.contains("wordpress", ignoreCase = true) -> "\uD83C\uDF10"  // WordPress globe
        issuer.contains("shopify", ignoreCase = true) -> "\uD83D\uDED2"    // Shopping cart
        issuer.contains("wix", ignoreCase = true) -> "\uD83C\uDFA8"        // Wix palette
        issuer.contains("medium", ignoreCase = true) -> "\uD83D\uDCF0"     // Medium newspaper
        issuer.contains("patreon", ignoreCase = true) -> "\uD83C\uDF97\uFE0F" // Patreon
        issuer.contains("hcaptcha", ignoreCase = true) ||
            issuer.contains("recaptcha", ignoreCase = true) -> "\uD83D\uDD13" // Captcha
        issuer.contains("samsung", ignoreCase = true) -> "\uD83D\uDCF1"    // Samsung
        issuer.contains("huawei", ignoreCase = true) -> "\uD83D\uDCF1"     // Huawei
        issuer.contains("xiaomi", ignoreCase = true) -> "\uD83D\uDCF1"     // Xiaomi
        issuer.contains("wordfence", ignoreCase = true) -> "\uD83D\uDEE1\uFE0F" // Shield
        issuer.contains("bank", ignoreCase = true) ||
            issuer.contains("bofa", ignoreCase = true) ||
            issuer.contains("chase", ignoreCase = true) ||
            issuer.contains("wells", ignoreCase = true) ||
            issuer.contains("capital", ignoreCase = true) -> "\uD83C\uDFE6" // Bank
        issuer.contains("school", ignoreCase = true) ||
            issuer.contains("edu", ignoreCase = true) ||
            issuer.contains("university", ignoreCase = true) -> "\uD83C\uDFEB" // School
        issuer.contains("admin", ignoreCase = true) ||
            issuer.contains("root", ignoreCase = true) -> "\uD83D\uDD11"   // Key
        else -> "\uD83D\uDD12"  // Default: lock
    }
}
