package com.cardclash.ui.deckbuilder

import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.Rarity

sealed interface DeckBuilderUiState {

    // ===== ENUMS =====
    enum class SlotValidationStatus { Empty, Filled, Error, Warning }
    enum class CardTypeFilter { ALL, ATTACK, HEAL, DRAW, STATUS, PASSIVE }
    enum class CopyFilter { All, Missing, Owned }
    enum class Severity { Error, Warning }

    // ===== DATA CLASSES =====
    data class DeckSlotUi(
        val index: Int,
        val cardId: CardId? = null,
        val name: String? = null,
        val rarity: Rarity? = null,
        val manaCost: Int? = null,
        val isPassive: Boolean = false,
        val state: SlotValidationStatus = SlotValidationStatus.Empty
    )

    data class CollectionCardUi(
        val cardId: CardId,
        val name: String,
        val rarity: Rarity,
        val manaCost: Int,
        val copiesOwned: Int,
        val maxCopies: Int,
        val canAdd: Boolean,
        val isPassive: Boolean,
        val isCompatible: Boolean = true,
        val isFavorite: Boolean = false,
        val effectDescription: String = ""
    )

    data class FilterState(
        val types: List<CardTypeFilter> = emptyList(),
        val rarities: List<Rarity> = emptyList(),
        val manaCosts: List<Int> = emptyList(),
        val onlyOwned: Boolean = true,
        val onlyCompatible: Boolean = true,
        val copyFilter: CopyFilter = CopyFilter.All
    )

    data class ValidationError(
        val code: String,
        val message: String,
        val slotIndex: Int? = null
    )

    data class ValidationWarning(
        val code: String,
        val message: String,
        val slotIndex: Int? = null
    )

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError>,
        val warnings: List<ValidationWarning>,
        val slotIssues: Map<Int, List<ValidationError>>
    ) {
        val hasErrors: Boolean = errors.isNotEmpty()
        val hasWarnings: Boolean = warnings.isNotEmpty()
        companion object {
            fun empty() = ValidationResult(true, emptyList(), emptyList(), emptyMap())
        }
    }

    data class ManaCurveData(
        val totalCards: Int,
        val byCost: Map<Int, Int>,
        val byCostAndRarity: Map<Int, Map<Rarity, Int>>,
        val passiveCount: Int
    ) {
        val maxCost: Int = byCost.keys.maxOrNull() ?: 5
    }

    data class DeckUiModel(
        val id: String?,
        val name: String,
        val slots: List<DeckSlotUi>,
        val nonPassiveCount: Int,
        val passiveCount: Int,
        val totalCopiesInDeck: Int,
        val isModified: Boolean = false
    ) {
        val isFull: Boolean
            get() = nonPassiveCount >= 8 || passiveCount >= 1
        val nonPassiveSlots: List<DeckSlotUi>
            get() = slots.filter { !it.isPassive }
        val passiveSlot: DeckSlotUi?
            get() = slots.firstOrNull { it.isPassive }
    }

    // ===== ESTADOS DE LA UI =====
    data class Loading(val isNewDeck: Boolean = true) : DeckBuilderUiState
    data class Editing(
        val deck: DeckUiModel,
        val collection: List<CollectionCardUi>,
        val filter: FilterState,
        val validation: ValidationResult,
        val manaCurve: ManaCurveData,
        val isCollectionExpanded: Boolean = false,
        val isSaving: Boolean = false,
        val saveError: String? = null
    ) : DeckBuilderUiState
    data class Saving(val deckName: String) : DeckBuilderUiState
    data class Saved(val message: String = "Mazo guardado") : DeckBuilderUiState
    data class Error(val message: String) : DeckBuilderUiState
    data class ConfirmDiscardChanges(
        val onConfirm: () -> Unit,
        val onCancel: () -> Unit
    ) : DeckBuilderUiState
}