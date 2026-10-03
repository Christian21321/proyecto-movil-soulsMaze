package com.cardclash.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.spacedBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toPx
import com.cardclash.domain.model.Rarity
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ManaCurveData
import com.cardclash.ui.theme.RarityCommonColor
import com.cardclash.ui.theme.RaritySsrColor
import com.cardclash.ui.theme.RaritySrColor
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.spacedBy
import androidx.compose.ui.text.style.TextAlign

@Composable
fun ManaCurveChart(
    data: ManaCurveData,
    modifier: Modifier = Modifier,
    onCostClick: (Int) -> Unit = {}
) {
    val maxCount = data.byCost.values.maxOrNull() ?: 0
    val maxCountForScale = maxCount.coerceAtLeast(8)
    val totalNonPassive = data.totalCards - data.passiveCount
    val averageCost = if (totalNonPassive > 0) {
        data.byCost.entries.sumOf { it.key * it.value }.toDouble() / totalNonPassive
    } else 0.0

    val accessibilitySummary = buildString {
        append("Curva de maná: promedio ${String.format("%.1f", averageCost)}, distribución")
        for (cost in 0..7) {
            val count = data.byCost[cost] ?: 0
            if (count > 0) {
                append(", $count de coste $cost")
            }
        }
        val count7Plus = data.byCost.entries.filter { it.key >= 7 }.sumOf { it.value }
        if (count7Plus > 0) {
            append(", $count7Plus de coste 7+")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Heading
                contentDescription = accessibilitySummary
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ManaCurveHeader(averageCost = averageCost)
        ManaCurveLegend()
        ManaCurveBars(
            data = data,
            maxCount = maxCountForScale,
            onCostClick = onCostClick
        )
    }
}

@Composable
private fun ManaCurveHeader(averageCost: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Curva de Maná",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = String.format("%.1f", averageCost),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = androidx.compose.ui.graphics.Color.Unspecified // Uses primary from theme
        )
    }
}

@Composable
private fun ManaCurveLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RarityLegendChip("Común", RarityCommonColor)
        RarityLegendChip("Super Rara", RaritySrColor)
        RarityLegendChip("Ultra Rara", RaritySsrColor)
        RarityLegendChip("Legendaria", Color(0xFFF57F17))
    }
}

@Composable
private fun RarityLegendChip(label: String, color: Color) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(6.dp))
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ManaCurveBars(
    data: ManaCurveData,
    maxCount: Int,
    onCostClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (cost in 0..7) {
            val is7Plus = cost == 7
            val count = if (is7Plus) {
                data.byCost.entries.filter { it.key >= 7 }.sumOf { it.value }
            } else {
                data.byCost[cost] ?: 0
            }
            val rarityBreakdown = if (is7Plus) {
                data.byCostAndRarity.entries
                    .filter { it.key >= 7 }
                    .flatMap { it.value.entries }
                    .groupBy { it.key }
                    .mapValues { it.value.sumOf { it.value } }
            } else {
                data.byCostAndRarity[cost] ?: emptyMap()
            }

            if (count > 0 || cost <= data.maxCost.coerceAtMost(7)) {
                ManaCostRow(
                    cost = cost,
                    count = count,
                    rarityBreakdown = rarityBreakdown,
                    maxCount = maxCount,
                    is7Plus = is7Plus,
                    onClick = { onCostClick(cost) }
                )
            }
        }
    }
}

@Composable
private fun ManaCostRow(
    cost: Int,
    count: Int,
    rarityBreakdown: Map<Rarity, Int>,
    maxCount: Int,
    is7Plus: Boolean,
    onClick: () -> Unit
) {
    val totalWidth = 200.dp
    val maxBarWidth = totalWidth * (8 / maxCount.toFloat())
    val labelText = if (is7Plus) "7+" else cost.toString()

    val commonCount = rarityBreakdown[Rarity.COMMON] ?: 0
    val srCount = rarityBreakdown[Rarity.SR] ?: 0
    val ssrCount = rarityBreakdown[Rarity.SSR] ?: 0
    val legendaryCount = 0 // LEGENDARY not in domain Rarity enum yet

    val accessibilityDesc = buildString {
        append("$count cartas de coste $labelText")
        val parts = mutableListOf<String>()
        if (commonCount > 0) parts.add("$commonCount★")
        if (srCount > 0) parts.add("$srCount★★")
        if (ssrCount > 0) parts.add("$ssrCount★★★")
        if (legendaryCount > 0) parts.add("$legendaryCount★★★★")
        if (parts.isNotEmpty()) {
            append(" — ${parts.joinToString(" ")}")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics {
                role = Role.Button
                contentDescription = accessibilityDesc
                selectable = true
                onClick(onClick)
            }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cost label
        Text(
            text = labelText,
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            style = androidx.compose.ui.text.TextStyle(
                lineHeight = 28.dp.toPx()
            )
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))

        // Stacked bar
        Box(
            modifier = Modifier
                .width(maxBarWidth.coerceAtMost(totalWidth))
                .height(28.dp)
                .background(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            var currentStart = 0f
            val segmentWidths = listOf(
                Rarity.COMMON to commonCount to RarityCommonColor,
                Rarity.SR to srCount to RaritySrColor,
                Rarity.SSR to ssrCount to RaritySsrColor,
                Rarity.COMMON to legendaryCount to Color(0xFFF57F17) // LEGENDARY
            ).filter { it.second > 0 }

            val totalSegments = segmentWidths.sumOf { it.second }
            if (totalSegments > 0) {
                segmentWidths.forEach { (rarity, segmentCount, color) ->
                    val segmentWidth = (segmentCount.toFloat() / totalSegments) * maxBarWidth
                    if (segmentWidth > 1.dp.toPx()) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = currentStart
                                }
                                .width(segmentWidth)
                                .height(28.dp)
                                .background(color, RoundedCornerShape(4.dp))
                        )
                    }
                    currentStart += segmentWidth
                }
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))

        // Count label
        if (count > 0) {
            Text(
                text = count.toString(),
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                style = androidx.compose.ui.text.TextStyle(
                    lineHeight = 28.dp.toPx()
                )
            )
        }
    }
}

