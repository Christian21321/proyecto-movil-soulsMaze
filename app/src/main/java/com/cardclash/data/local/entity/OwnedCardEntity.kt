package com.cardclash.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tabla de coleccion de cartas del jugador (`owned_cards`).
 *
 * Una fila por [CardId] poseida, con el numero de copias [ownedCount].
 * El limite de copias NO se valida en SQL: se valida en la capa de repositorio
 * contra [com.cardclash.domain.progression.ProgressionRules] antes de escribir.
 *
 * ## Integridad referencial (decision de diseno)
 * NO se declara clave foranea hacia el catalogo de cartas: el catalogo vive en
 * el dominio ([com.cardclash.domain.engine.DefaultCardCatalog]), no como tabla
 * Room. La existencia de la carta y el limite de copias se comprueban en
 * [com.cardclash.data.PlayerProgressRepository] (via [com.cardclash.domain.repository.CardCatalog]
 * y [com.cardclash.domain.progression.ProgressionRules]) en cada escritura.
 */
@Entity(tableName = "owned_cards")
data class OwnedCardEntity(
    @PrimaryKey
    @ColumnInfo(name = "card_id")
    val cardId: String,
    @ColumnInfo(name = "owned_count")
    val ownedCount: Int,
)