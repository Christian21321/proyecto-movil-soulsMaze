package com.cardclash.ui.collection

import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.Rarity
import com.cardclash.domain.progression.Tier

/**
 * Fila de coleccion: carta del catalogo fusionada con las copias poseidas y el
 * limite derivado del nivel actual.
 */
data class CollectionCardUi(
    val cardId: CardId,
    val name: String,
    val rarity: Rarity,
    /** Coste de mana derivado de la rareza (3=COMMON, 4=SR, 5=SSR). */
    val manaCost: Int,
    val copiesOwned: Int,
    val maxCopies: Int,
    val canAdd: Boolean,
    val canRemove: Boolean,
)

/**
 * Estado UI de coleccion + progresion (UDF): cabecera de progreso derivada del
 * XP total y lista de cartas del catalogo enriquecida con copias y limites.
 */
data class CollectionUiState(
    val isLoading: Boolean = true,
    val level: Int = 1,
    val tier: Tier = Tier.T1,
    val xpTotal: Int = 0,
    val xpIntoLevel: Int = 0,
    val xpNeededForNextLevel: Int? = null,
    val xpProgressFraction: Float = 0f,
    val maxCopiesAllowed: Int = 2,
    val cards: List<CollectionCardUi> = emptyList(),
)