package com.cardclash.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.cardclash.data.local.entity.DeckEntity
import com.cardclash.data.local.entity.DeckCardEntity

/**
 * POJO compuesto para consultar un mazo con sus cartas en una sola transaccion
 * via @Relation de Room.
 *
 * Room rellena automaticamente [cards] ejecutando:
 * SELECT * FROM deck_cards WHERE deck_id = :deckId ORDER BY slot
 */
data class DeckWithCards(
    @Embedded
    val deck: DeckEntity,

    @Relation(
        parentColumn = "deck_id",
        entityColumn = "deck_id",
        entity = DeckCardEntity::class,
        projection = ["slot", "card_id"],
    )
    val cards: List<DeckCardEntity>,
) {
    /** true si el mazo existe (deck != null) y tiene cartas. */
    val hasCards: Boolean get() = cards.isNotEmpty()
}