package com.cardclash.ui.deckbuilder

import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.Rarity

/**
 * Estado de la pantalla del constructor de mazos (MVVM/UDF).
 *
 * El mazo tiene 9 huecos fijos: 0..7 para cartas normales y el 8 para la
 * carta pasiva (coherente con [com.cardclash.domain.service.DeckRuleService]:
 * máx. 8 no pasivas + 1 pasiva extra).
 */
sealed interface DeckBuilderUiState {

    /** Tipo de carta para el filtro de la colección. */
    enum class CardTypeFilter(val label: String) {
        ATTACK("Ataque"),
        HEAL("Curación"),
        DRAW("Robo"),
        STATUS("Estado"),
        PASSIVE("Pasiva"),
    }

    /** Un hueco del mazo; [cardId] es null si está vacío. */
    data class DeckSlotUi(
        val index: Int,
        val isPassiveSlot: Boolean,
        val cardId: CardId? = null,
        val name: String? = null,
        val rarity: Rarity? = null,
        val manaCost: Int? = null,
        val hasError: Boolean = false,
    ) {
        val isEmpty: Boolean get() = cardId == null
    }

    /** Carta del catálogo tal como se muestra en la colección. */
    data class CollectionCardUi(
        val cardId: CardId,
        val name: String,
        val rarity: Rarity,
        val manaCost: Int,
        val type: CardTypeFilter,
        val copiesOwned: Int,
        val isPassive: Boolean,
        val effectDescription: String,
        /** true si la carta ya está en el mazo (solo se permite 1 copia). */
        val inDeck: Boolean,
        /** true si se puede añadir ahora (poseída, no está en el mazo y hay hueco). */
        val canAdd: Boolean,
    )

    /** Filtros de la colección. Listas vacías = sin filtrar por ese criterio. */
    data class FilterState(
        val types: Set<CardTypeFilter> = emptySet(),
        val onlyOwned: Boolean = true,
    )

    data class ValidationResult(
        val errors: List<String>,
        /** Índices de hueco con algún problema (p. ej. carta no poseída). */
        val slotsWithErrors: Set<Int>,
    ) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    data class DeckUiModel(
        val id: String?,
        val name: String,
        val slots: List<DeckSlotUi>,
        val isModified: Boolean = false,
    ) {
        val nonPassiveCount: Int get() = slots.count { !it.isPassiveSlot && !it.isEmpty }
        val passiveCount: Int get() = slots.count { it.isPassiveSlot && !it.isEmpty }
        val cardIds: List<CardId> get() = slots.mapNotNull { it.cardId }
    }

    /** Número de cartas por coste de mana (solo cartas no pasivas). */
    data class ManaCurveData(val byCost: Map<Int, Int>) {
        val maxCount: Int get() = byCost.values.maxOrNull() ?: 0
    }

    data object Loading : DeckBuilderUiState

    data class Editing(
        val deck: DeckUiModel,
        /** Colección ya filtrada según [filter]. */
        val collection: List<CollectionCardUi>,
        val filter: FilterState,
        val validation: ValidationResult,
        val manaCurve: ManaCurveData,
        val isSaving: Boolean = false,
        val saveError: String? = null,
        /** true mientras se pide confirmación para salir sin guardar. */
        val confirmDiscard: Boolean = false,
    ) : DeckBuilderUiState

    data object Saved : DeckBuilderUiState

    data class Error(val message: String) : DeckBuilderUiState

    companion object {
        /** Huecos para cartas normales (0..7). */
        const val NON_PASSIVE_SLOTS: Int = 8

        /** Índice del hueco de la carta pasiva. */
        const val PASSIVE_SLOT_INDEX: Int = 8
    }
}
