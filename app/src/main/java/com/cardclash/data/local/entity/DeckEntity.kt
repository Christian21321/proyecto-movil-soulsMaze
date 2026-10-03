package com.cardclash.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabla de mazos del jugador (`decks`).
 *
 * Un jugador puede tener multiples mazos, pero solo uno puede estar activo
 * ([isActive] = true). El indice unico en [name] + [playerId] garantiza que
 * no haya dos mazos con el mismo nombre para el mismo jugador.
 *
 * ## Integridad referencial
 * [playerId] referencia [PlayerProfileEntity] con borrado/actualizacion en
 * cascada: al borrar el perfil se borran sus mazos (y las cartas de los mazos
 * via [DeckCardEntity] con ON DELETE CASCADE).
 */
@Entity(
    tableName = "decks",
    foreignKeys = [
        ForeignKey(
            entity = PlayerProfileEntity::class,
            parentColumns = ["player_id"],
            childColumns = ["player_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["name", "player_id"], unique = true),
        Index("player_id"),
    ],
)
data class DeckEntity(
    @PrimaryKey
    @ColumnInfo(name = "deck_id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "is_active", defaultValue = "0")
    val isActive: Boolean,

    @ColumnInfo(name = "player_id")
    val playerId: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        fun create(
            name: String,
            playerId: String = PlayerProfileEntity.DEFAULT_PLAYER_ID,
            clock: () -> Long = System::currentTimeMillis,
        ): DeckEntity {
            val now = clock()
            val id = java.util.UUID.randomUUID().toString()
            return DeckEntity(
                id = id,
                name = name,
                isActive = false,
                playerId = playerId,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}