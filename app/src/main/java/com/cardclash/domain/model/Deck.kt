package com.cardclash.domain.model

/**
 * Mazo de cartas de un jugador.
 *
 * Representa la lista circular de cartas en el mazo. El motor se apoya en
 * [Deck] para resolver el robo y el comportamiento circular con re-barajado.
 */
data class Deck(
    val playerId: PlayerId,
    val cards: List<InstanceId>,
) {
    init {
        require(playerId.value.isNotBlank()) { "El mazo requiere un jugador" }
    }

    /** Cartas restantes en la cola actual (antes de re-barajar, si se agota). */
    val count: Int get() = cards.size

    /** true si el mazo no tiene cartas (deberia re-barajarse o agotarse). */
    val isEmpty: Boolean get() = cards.isEmpty()
}
