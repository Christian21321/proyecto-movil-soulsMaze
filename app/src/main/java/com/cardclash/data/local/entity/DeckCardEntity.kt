package com.cardclash.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabla de cartas dentro de un mazo (`deck_cards`).
 *
 * Relacion 1:N con [DeckEntity]: un mazo tiene 0-9 cartas (slots 0-8).
 * La clave primaria compuesta es [deckId] + [slot] para garantizar:
 * - Un slot por carta en el mazo (no duplicados de posicion).
 * - Orden estable del mazo (el slot define la posicion en la cola circular).
 *
 * ## Integridad referencial
 * [deckId] referencia [DeckEntity] con ON DELETE CASCADE: al borrar un mazo
 * se borran automaticamente todas sus cartas.
 * [cardId] NO tiene FK a catalogo (el catalogo vive en el dominio, no en Room).
 */
@Entity(
    tableName = "deck_cards",
    primaryKeys = ["deck_id", "slot"],
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["deck_id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("deck_id"),
    ],
)
data class DeckCardEntity(
    @ColumnInfo(name = "deck_id")
    val deckId: String,

    @ColumnInfo(name = "slot")
    val slot: Int,

    @ColumnInfo(name = "card_id")
    val cardId: String,
) {
    init {
        require(slot in 0..8) { "Slot must be in 0..8, got $slot" }
        require(cardId.isNotBlank()) { "CardId cannot be blank" }
    }
}