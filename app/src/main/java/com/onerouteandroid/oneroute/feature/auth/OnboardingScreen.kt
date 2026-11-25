package com.onerouteandroid.oneroute.feature.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onerouteandroid.oneroute.core.components.PrimaryPillButton
import com.onerouteandroid.oneroute.core.components.StatusPill
import com.onerouteandroid.oneroute.core.components.TonalPillButton
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.Route
import com.onerouteandroid.oneroute.core.theme.CanvasBackground
import com.onerouteandroid.oneroute.core.theme.CardSurface
import com.onerouteandroid.oneroute.core.theme.EcoContainer
import com.onerouteandroid.oneroute.core.theme.EcoTeal
import com.onerouteandroid.oneroute.core.theme.OutlineSoft
import com.onerouteandroid.oneroute.core.theme.PrimaryBlue
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueDark
import com.onerouteandroid.oneroute.core.theme.PrimaryBlueLight
import com.onerouteandroid.oneroute.core.theme.PrimaryContainer
import com.onerouteandroid.oneroute.core.theme.SoftContainer
import com.onerouteandroid.oneroute.core.theme.TextPrimary
import com.onerouteandroid.oneroute.core.theme.TextSecondary

private data class OnboardingSlide(val title: String, val body: String, val badgeTop: String, val badgeBottom: String)

private val slides = listOf(
    OnboardingSlide(
        title = "Viaja cómodo, ahorra y cuida el planeta",
        body = "Conecta con conductores y pasajeros verificados que van a tu mismo destino todos los días de forma segura y económica.",
        badgeTop = "100% Verificados",
        badgeBottom = "-45% huella de CO₂"
    ),
    OnboardingSlide(
        title = "Comparte gastos en cada trayecto diario",
        body = "Divide gasolina y casetas con personas de confianza, con tarifas claras y pagos directos desde tu billetera.",
        badgeTop = "0% comisión inicial",
        badgeBottom = "Ahorra hasta 65%"
    ),
    OnboardingSlide(
        title = "Seguimiento en vivo y abordaje con PIN",
        body = "Comparte tu ruta en tiempo real, valida tu ascenso con código seguro y cuenta con soporte 24/7.",
        badgeTop = "Monitoreo GPS 24/7",
        badgeBottom = "Botón SOS activo"
    )
)

@Composable
fun OnboardingScreen(nav: AppNavigator) {
    val pagerState = rememberPagerState(pageCount = { slides.size })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            StatusPill("RutaCompartida", container = SoftContainer, content = PrimaryBlueDark, icon = Icons.Rounded.DirectionsCar)
            TextButton(onClick = { nav.resetTo(Route.Login) }) {
                Text("Saltar", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(8.dp))

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { index ->
            val slide = slides[index]
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                OnboardingIllustration(slide.badgeTop, slide.badgeBottom, index)
                Spacer(Modifier.height(22.dp))
                Text(slide.title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(slide.body, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(slides.size) { index ->
                val selected = pagerState.currentPage == index
                val width by animateDpAsState(if (selected) 28.dp else 8.dp, label = "dotW")
                val color by animateColorAsState(if (selected) PrimaryBlue else OutlineSoft, label = "dotC")
                Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
            }
        }

        Spacer(Modifier.height(28.dp))

        PrimaryPillButton(
            text = "Registrarse",
            trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
            onClick = { nav.navigate(Route.Register) }
        )
        Spacer(Modifier.height(12.dp))
        TonalPillButton(
            text = "Iniciar sesión",
            onClick = { nav.navigate(Route.Login) }
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { nav.resetTo(Route.Home) }) {
            Text("Explorar viajes como invitado", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OnboardingIllustration(badgeTop: String, badgeBottom: String, page: Int) {
    Surface(color = SoftContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().height(276.dp)) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))) {
                drawRect(
                    Brush.verticalGradient(listOf(Color(0xFFDCEBFF), Color(0xFFF3F8FF), Color(0xFFE0ECFF))),
                    size = size
                )
                drawCircle(Color(0xFFFFF3C4), radius = 32f, center = Offset(size.width * 0.5f, size.height * 0.24f))
                val buildingColor = Color(0xFFC7DAF6)
                drawRoundRect(buildingColor, Offset(size.width * 0.08f, size.height * 0.34f), Size(size.width * 0.16f, size.height * 0.38f), CornerRadius(12f))
                drawRoundRect(Color(0xFFB5CFF2), Offset(size.width * 0.26f, size.height * 0.22f), Size(size.width * 0.18f, size.height * 0.5f), CornerRadius(12f))
                drawRoundRect(buildingColor, Offset(size.width * 0.6f, size.height * 0.28f), Size(size.width * 0.16f, size.height * 0.44f), CornerRadius(12f))
                drawRoundRect(Color(0xFFB5CFF2), Offset(size.width * 0.78f, size.height * 0.36f), Size(size.width * 0.14f, size.height * 0.36f), CornerRadius(12f))
                drawRect(Color(0xFF94A3B8), topLeft = Offset(0f, size.height * 0.72f), size = Size(size.width, size.height * 0.28f))
                val carX = size.width * (0.24f + page * 0.03f)
                val carY = size.height * 0.54f
                drawRoundRect(PrimaryBlueLight, Offset(carX, carY), Size(size.width * 0.52f, size.height * 0.2f), CornerRadius(32f))
                drawRoundRect(PrimaryContainer, Offset(carX + size.width * 0.08f, carY - size.height * 0.11f), Size(size.width * 0.34f, size.height * 0.14f), CornerRadius(24f))
                drawCircle(Color(0xFF1E293B), radius = 26f, center = Offset(carX + size.width * 0.12f, carY + size.height * 0.2f))
                drawCircle(Color(0xFF1E293B), radius = 26f, center = Offset(carX + size.width * 0.4f, carY + size.height * 0.2f))
                drawCircle(Color.White, radius = 10f, center = Offset(carX + size.width * 0.12f, carY + size.height * 0.2f))
                drawCircle(Color.White, radius = 10f, center = Offset(carX + size.width * 0.4f, carY + size.height * 0.2f))
            }
            StatusPill(
                badgeTop,
                container = CardSurface,
                content = TextPrimary,
                icon = Icons.Rounded.VerifiedUser,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
            StatusPill(
                badgeBottom,
                container = CardSurface,
                content = EcoTeal,
                icon = Icons.Rounded.Eco,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            )
        }
    }
}
