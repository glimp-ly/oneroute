package com.oneroute.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.oneroute.core.components.AppCard
import com.oneroute.core.components.AvatarBadge
import com.oneroute.core.components.RatingLabel
import com.oneroute.core.components.StatusPill
import com.oneroute.core.theme.DangerContainer
import com.oneroute.core.theme.DangerRed
import com.oneroute.core.theme.OutlineSoft
import com.oneroute.core.theme.PrimaryBlue
import com.oneroute.core.theme.PrimaryBlueDark
import com.oneroute.core.theme.PrimaryContainer
import com.oneroute.core.theme.SoftContainer
import com.oneroute.core.theme.TextMuted
import com.oneroute.core.theme.TextPrimary
import com.oneroute.core.theme.TextSecondary
import com.oneroute.data.model.AvailableTrip

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AvailableTripCard(
    trip: AvailableTrip,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    AppCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AvatarBadge(trip.driver.initials, size = 44, verified = trip.driver.verified)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(trip.driver.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        trip.driver.badge?.let { StatusPill(it, container = SoftContainer, content = PrimaryBlueDark) }
                    }
                    RatingLabel(trip.driver.rating, suffix = "• ${trip.driver.totalTrips} viajes")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("S/ ${trip.price}", style = MaterialTheme.typography.headlineSmall, color = PrimaryBlue)
                    Text("PEN / lugar", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }

            Surface(color = SoftContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(trip.departure, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(trip.origin, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("${trip.durationMin} min", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(PrimaryBlue))
                            Box(Modifier.width(52.dp).height(2.dp).background(OutlineSoft))
                            Box(Modifier.size(6.dp).clip(CircleShape).background(PrimaryBlueDark))
                        }
                        Text(trip.via, style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                        Text(trip.arrival, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(trip.destination, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            if (compact) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SeatsPill(trip.seatsLeft)
                    Text("${trip.driver.vehicle} (${trip.driver.color})", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    trip.perks.forEach { StatusPill(it, container = SoftContainer, content = TextSecondary) }
                    SeatsPill(trip.seatsLeft)
                }
            }
        }
    }
}

@Composable
private fun SeatsPill(seatsLeft: Int) {
    if (seatsLeft == 1) {
        StatusPill("¡Último asiento!", container = DangerContainer, content = DangerRed, icon = Icons.Rounded.Warning)
    } else {
        StatusPill(
            text = "$seatsLeft asientos disponibles",
            container = PrimaryContainer,
            content = PrimaryBlueDark,
            icon = Icons.Rounded.AirlineSeatReclineNormal
        )
    }
}
