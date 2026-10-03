package com.cardclash.data.local.model

import com.cardclash.domain.model.PlayerId

/**
 * Mazo de cartas completo (modelo de dominio local, Kotlin puro).
 *
 * Inmutable: todos los campos son val. Representa un mazo guardado con su
 * metadata (nombre, activo, timestamps) y la lista ordenada de cartas.
 *
 * La conversion a/desde entidades Room la hace [DeckMapper].
 */
data class Deck(
    /** Identificador unico del mazo (UUID). */
    val id: DeckId,

    /** Nombre visible del mazo (unico por jugador). */
    val name: String,

    /** true si este es el mazo activo del jugador. */
    val isActive: Boolean,

    /** Identificador del jugador propietario. */
    val playerId: PlayerId,

    /** Timestamp de creacion (epoch millis). */
    val createdAt: Long,

    /** Timestamp de ultima actualizacion (epoch millis). */
    val updatedAt: Long,

    /** Cartas del mazo ordenadas por slot (0..8). */
    val cards: List<DeckCard>,
) {
    init {
        require(id.value.isNotBlank()) { "Deck requires non-blank id" }
        require(name.isNotBlank()) { "Deck requires non-blank name" }
        require(playerId.value.isNotBlank()) { "Deck requires non-blank playerId" }
        require(cards.size <= 9) { "Deck cannot have more than 9 cards (slots 0-8)" }
        // Validar que no hay slots duplicados
        val slots = cards.map { it.slot }
        require(slots.distinct().size == slots.size) { "Duplicate slots in deck cards" }
        require(slots.all { it in 0..8 }) { "All slots must be in 0..8" }
    }

    /** Numero de cartas en el mazo (0-9). */
    val size: Int get() = cards.size

    /** true si el mazo no tiene cartas. */
    val isEmpty: Boolean get() = cards.isEmpty()

    /** Cartas ordenadas por slot (ya vienen ordenadas, pero garantiza). */
    val cardsBySlot: List<DeckCard> get() = cards.sortedBy { it.slot }
}