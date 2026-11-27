package com.oneroute.core.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oneroute.R
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary

val LocalOpenAppMenu = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    size: Int = 36,
    onClick: (() -> Unit)? = null,
    contentDescription: String = if (onClick != null) "Abrir menú" else "One Route"
) {
    val logoModifier = modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(PrimaryBlue)
        .let { base ->
            if (onClick != null) base.clickable(onClickLabel = "Abrir menú", onClick = onClick) else base
        }

    Box(
        modifier = logoModifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.oneroute_logo_high),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showBrandLogo: Boolean = false,
    onBrandLogoClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null,
    unreadNotifications: Int = 0,
    avatarInitials: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = CardSurface, shadowElevation = 1.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                }
            }
            if (showBrandLogo) {
                val openMenu = onBrandLogoClick ?: LocalOpenAppMenu.current
                BrandLogo(
                    modifier = Modifier.padding(start = if (onBack == null) 4.dp else 0.dp),
                    onClick = openMenu
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
            if (onNotifications != null) {
                IconButton(onClick = onNotifications) {
                    BadgedBox(badge = { if (unreadNotifications > 0) Badge { Text(unreadNotifications.toString()) } }) {
                        Icon(Icons.Rounded.Notifications, contentDescription = "Notificaciones", tint = TextSecondary)
                    }
                }
            }
            if (onHelp != null) {
                IconButton(onClick = onHelp) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = "Ayuda", tint = TextSecondary)
                }
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = TextSecondary)
                }
            }
            if (avatarInitials != null) {
                Spacer(Modifier.width(4.dp))
                AvatarBadge(
                    initials = avatarInitials,
                    size = 36,
                    modifier = if (onAvatarClick != null) Modifier.clip(CircleShape).clickable(onClick = onAvatarClick) else Modifier
                )
            }
        }
    }
}
