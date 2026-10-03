package com.cardclash.ui.deckbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.navigation.compose.calculateWindowSizeClass
import androidx.window.layout.WindowWidthSizeClass
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ValidationResult
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.SlotValidationStatus
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.DeckSlotUi
import com.cardclash.ui.theme.CardClashTheme

@Composable
fun SlotsRow(
    slots: List<DeckSlotUi>,
    onClick: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDetail: (Int) -> Unit,
    validation: ValidationResult,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (widthSizeClass == WindowWidthSizeClass.COMPACT) 8.dp else 12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        items(slots, key = { it.index }) { slot ->
            val width = if (widthSizeClass == WindowWidthSizeClass.COMPACT) 72.dp else 96.dp
            DeckSlot(
                slot = slot,
                onClick = { onClick(slot.index) },
                onRemove = { onRemove(slot.index) },
                onDetail = { onDetail(slot.index) },
                slotErrors = validation.slotIssues[slot.index] ?: emptyList(),
                isCompact = widthSizeClass == WindowWidthSizeClass.COMPACT,
                modifier = Modifier.width(width)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SlotsRowPreview() {
    CardClashTheme {
        val slots = (0..8).map { i ->
            DeckSlotUi(
                index = i,
                state = when {
                    i < 3 -> SlotValidationStatus.Filled
                    i == 3 -> SlotValidationStatus.Error
                    i == 4 -> SlotValidationStatus.Warning
                    else -> SlotValidationStatus.Empty
                },
                cardId = if (i < 3) com.cardclash.domain.model.CardId("attack-$i") else null,
                name = if (i < 3) "Golpe $i" else null,
                rarity = if (i < 3) com.cardclash.domain.model.Rarity.COMMON else null,
                manaCost = if (i < 3) 3 else null,
                isPassive = i == 8
            )
        }
        SlotsRow(
            slots = slots,
            onClick = {},
            onRemove = {},
            onDetail = {},
            validation = ValidationResult.empty(),
            widthSizeClass = androidx.window.layout.WindowWidthSizeClass.EXPANDED,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
}