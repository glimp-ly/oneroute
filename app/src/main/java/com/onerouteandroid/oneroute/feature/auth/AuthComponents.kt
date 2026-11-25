package com.onerouteandroid.oneroute.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.theme.DangerRed
import com.onerouteandroid.oneroute.core.theme.OutlineSoft
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.SuccessGreen
import com.onerouteandroid.oneroute.core.theme.TextMuted
import com.onerouteandroid.oneroute.core.theme.WarningAmber

internal fun passwordStrength(password: String): Int {
    if (password.isEmpty()) return 0
    var score = 0
    if (password.length >= 8) score++
    if (password.any { it.isDigit() } && password.any { it.isUpperCase() }) score++
    if (password.any { it.isLowerCase() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return score.coerceIn(1, 4)
}

@Composable
internal fun PasswordStrengthBar(password: String, modifier: Modifier = Modifier) {
    val score = passwordStrength(password)
    val (label, activeColor) = when (score) {
        0 -> "" to OutlineSoft
        1 -> "Débil" to DangerRed
        2 -> "Media" to WarningAmber
        3 -> "Fuerte (75%)" to PrimaryBlue
        else -> "Muy segura" to SuccessGreen
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Fortaleza de contraseña", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            if (label.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = activeColor)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..4) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i <= score) activeColor else OutlineSoft)
                )
            }
        }
    }
}

internal fun isValidEmail(text: String): Boolean {
    val trimmed = text.trim()
    val at = trimmed.indexOf('@')
    val dot = trimmed.lastIndexOf('.')
    return at > 0 && dot > at + 1 && dot < trimmed.length - 1
}

internal fun isValidAccountInput(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.contains("@") || trimmed.count { it.isDigit() } >= 8
}
