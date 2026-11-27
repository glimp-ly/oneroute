package com.oneroute.core.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.oneroute.core.theme.CardSurface
import com.oneroute.core.theme.DangerRed
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.TextSecondary

enum class MainTab(val label: String, val icon: ImageVector) {
    INICIO("Inicio", Icons.Rounded.Home),
    BUSCAR("Buscar", Icons.Rounded.Search),
    MIS_VIAJES("Mis Viajes", Icons.Rounded.DirectionsCar),
    PERFIL("Perfil", Icons.Rounded.Person)
}

@Composable
fun AppBottomBar(
    current: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    tripBadge: Int = 0
) {
    NavigationBar(containerColor = CardSurface, tonalElevation = 3.dp, modifier = modifier) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = {
                    BadgedBox(badge = {
                        if (tab == MainTab.MIS_VIAJES && tripBadge > 0) {
                            Badge(containerColor = DangerRed) { Text(tripBadge.toString()) }
                        }
                    }) {
                        Icon(tab.icon, contentDescription = tab.label)
                    }
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = PrimaryBlue,
                    indicatorColor = PrimaryBlue,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
            )
        }
    }
}
