package com.cardclash.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardclash.domain.progression.Tier
import com.cardclash.ui.theme.RarityOnBadgeColor

/**
 * Cabecera de progreso compartida (menu y coleccion): nivel, tramo, XP total,
 * barra de progreso dentro del nivel y etiqueta de XP sobre la necesaria.
 */
@Composable
fun ProgressSummary(
    level: Int,
    tier: Tier,
    xpTotal: Int,
    xpIntoLevel: Int,
    xpNeededForNextLevel: Int?,
    progressFraction: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Nivel $level",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(12.dp))
            TierBadge(tier = tier)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tramo ${tier.name} · $xpTotal XP total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        // xpNeededForNextLevel es XP TOTAL; la etiqueta muestra la XP de este nivel.
        val xpLabel = xpNeededForNextLevel?.let { totalForNext ->
            val levelCost = totalForNext - (xpTotal - xpIntoLevel)
            "$xpIntoLevel / $levelCost XP para nivel ${level + 1}"
        } ?: "Nivel maximo alcanzado"
        Text(
            text = xpLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Badge del tramo de progresion (T1..T5) sobre violeta de marca. */
@Composable
fun TierBadge(tier: Tier, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = tier.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Badge de rareza (COMMON/SR/SSR) con color semantico y texto blanco. */
@Composable
fun RarityBadge(
    label: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = badgeColor,
        contentColor = RarityOnBadgeColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}