package com.cardclash.domain.progression

import com.cardclash.domain.model.CardId

/**
 * Coleccion de cartas de un jugador: asociacion inmutable CardId -> copias.
 *
 * Es un valor inmutable [data class]; cada operacion que modifica el numero de
 * copias producira una NUEVA instancia. Las operaciones de escritura validan
 * los limites de copias contra [ProgressionRules] segun el nivel del jugador.
 */
data class CardCollection(
    private val owned: Map<CardId, Int> = emptyMap(),
) {

    init {
        owned.forEach { (id, copies) ->
            require(copies >= 0) { "La carta $id no puede tener copias negativas" }
        }
    }

    /** Copias que el jugador posee de [cardId] (0 si no posee ninguna). */
    fun ownedCount(cardId: CardId): Int = owned[cardId] ?: 0

    /** true si se puede anadir una copia mas de [cardId] a [level]. */
    fun canAddCopy(cardId: CardId, rules: ProgressionRules, level: Int): Boolean =
        ownedCount(cardId) < rules.maxCopiesAllowed(level)

    /**
     * Anade una copia de [cardId] a [level], validando contra
     * [ProgressionRules.maxCopiesAllowed]. Lanza [IllegalArgumentException] si
     * el limite se superaria.
     */
    fun addCopy(cardId: CardId, rules: ProgressionRules, level: Int): CardCollection {
        require(canAddCopy(cardId, rules, level)) {
            "No se pueden anadir mas copias de $cardId a nivel $level (max ${rules.maxCopiesAllowed(level)})"
        }
        return CardCollection(owned + (cardId to (ownedCount(cardId) + 1)))
    }

    /**
     * Elimina una copia de [cardId]. Si la carta llegaba a 0 copias, se elimina
     * del mapa. Lanza [IllegalArgumentException] si no se poseian copias.
     */
    fun removeCopy(cardId: CardId): CardCollection {
        require(ownedCount(cardId) > 0) { "La carta $cardId no tiene copias que eliminar" }
        val next = ownedCount(cardId) - 1
        return if (next == 0) {
            CardCollection(owned - cardId)
        } else {
            CardCollection(owned + (cardId to next))
        }
    }

    /** Lista inmutable de CardId que el jugador posee con al menos 1 copia. */
    fun ownedCards(): Set<CardId> = owned.keys
}
