package com.cardclash.data.local.model

import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.PlayerId

/**
 * Carta dentro de un mazo (modelo de dominio local, Kotlin puro).
 *
 * Representa una carta en una posicion concreta (slot 0-8) del mazo.
 * Inmutable: slot y cardId son val.
 */
data class DeckCard(
    /** Posicion en el mazo (0-8), define el orden de robo circular. */
    val slot: Int,
    /** Identificador de catalogo de la carta en esta posicion. */
    val cardId: CardId,
) {
    init {
        require(slot in 0..8) { "Slot must be in 0..8, got $slot" }
    }
}