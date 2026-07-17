package com.auth2fa.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
@Composable
fun AccountCard(
    account: Account,
    codeEntry: CodeEntry?,
    isCopied: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val code = codeEntry?.code
    val remaining = codeEntry?.remainingSeconds ?: 0
    val progress = 1f - (codeEntry?.progress ?: 0f)

    // Animated progress color
    val progressColor by animateColorAsState(
        targetValue = when {
            remaining <= 5 -> Error
            remaining <= 10 -> Warning
            else -> MaterialTheme.colorScheme.primary
        },
        label = "progressColor"
    )

    // Card elevation animation on copy
    val cardAlpha by animateFloatAsState(
        targetValue = if (isCopied) 0.95f else 1f,
        animationSpec = tween(300),
        label = "cardAlpha"
    )

    val accentColor = getAccentColor(account.issuer)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCopy() },
        shape = RoundedCornerShape(RoundedLg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = cardAlpha)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCopied) 4.dp else 2.dp)
    ) {
        Box {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .padding(vertical = 14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.6f))
                    .align(Alignment.CenterStart)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Service icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(RoundedSm))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getIssuerEmoji(account.issuer),
                            fontSize = 22.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Issuer & Name
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.issuer,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
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
                    }

                    // Code
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.height(IntrinsicSize.Min)
                    ) {
                        if (isCopied) {
                            Text(
                                text = "✓ 已复制",
                                color = Success,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        } else if (code != null) {
                            Text(
                                text = "${code.substring(0, 3)} ${code.substring(3)}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                letterSpacing = 4.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                                fontFeatureSettings = "tnum"
                            )
                        } else {
                            Text(
                                text = "— —",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Countdown progress bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(progressColor)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = if (code != null) "$remaining" else "--",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Deterministic accent color for an issuer name.
 */
@Composable
fun getAccentColor(issuer: String): Color {
    val hash = issuer.lowercase().hashCode()
    val colors = listOf(
        Color(0xFF6C63FF),
        Color(0xFFF5576C),
        Color(0xFF4FACFE),
        Color(0xFF43E97B),
        Color(0xFFFA709A),
        Color(0xFFA18CD1),
        Color(0xFFFCCB90),
        Color(0xFF30CFD0),
        Color(0xFFF093FB),
    )
    return colors[((hash % colors.size) + colors.size) % colors.size]
}

/**
 * Emoji for common issuers.
 */
fun getIssuerEmoji(issuer: String): String {
    val lower = issuer.lowercase()
    return when {
        "github" in lower -> "🐙"
        "google" in lower -> "🟢"
        "microsoft" in lower || "azure" in lower || "outlook" in lower -> "🔷"
        "apple" in lower -> "🍎"
        "facebook" in lower || "meta" in lower -> "📘"
        "twitter" in lower || "x.com" in lower -> "🐦"
        "discord" in lower -> "🎮"
        "slack" in lower -> "💬"
        "telegram" in lower -> "✈️"
        "whatsapp" in lower -> "💚"
        "aws" in lower || "amazon" in lower -> "☁️"
        "cloudflare" in lower -> "🌤"
        "gitlab" in lower -> "🦊"
        "bitbucket" in lower -> "🔵"
        "docker" in lower -> "🐳"
        "npm" in lower -> "📦"
        "stripe" in lower -> "💳"
        "paypal" in lower -> "💰"
        "shopify" in lower -> "🛍"
        "vercel" in lower -> "▲"
        "netlify" in lower -> "⚡"
        "heroku" in lower -> "🧪"
        "digitalocean" in lower -> "🌊"
        "steam" in lower -> "🎮"
        "nintendo" in lower -> "🎮"
        "adobe" in lower -> "🔺"
        "figma" in lower -> "🖌"
        "notion" in lower -> "📝"
        "firebase" in lower -> "🔥"
        "mongodb" in lower -> "🍃"
        "redis" in lower -> "🔴"
        "postgres" in lower || "postgresql" in lower -> "🐘"
        "auth0" in lower -> "🔑"
        "duo" in lower -> "🔐"
        "wordpress" in lower -> "📝"
        "dropbox" in lower -> "📦"
        "instagram" in lower -> "📷"
        else -> "🔑"
    }
}
