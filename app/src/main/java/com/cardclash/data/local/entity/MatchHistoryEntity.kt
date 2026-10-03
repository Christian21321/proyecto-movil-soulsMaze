package com.cardclash.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabla de historial de partidas (`match_history`).
 *
 * Una fila por partida: [matchId] (PK), el [PlayerId] que la jugo, resultado
 * ([victory]), XP otorgado ([xpEarned]), momento en epoch millis ([playedAt]) y
 * un resumen breve opcional ([summary]).
 *
 * ## Integridad referencial
 * [playerId] referencia [PlayerProfileEntity] con borrado/actualizacion en
 * cascada: una partida siempre pertenece a un perfil existente. Los indices en
 * [playerId] y [playedAt] aceleran la consulta "N ultimas partidas del jugador".
 */
@Entity(
    tableName = "match_history",
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
        Index("player_id"),
        Index(value = ["played_at"]),
    ],
)
data class MatchHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "match_id")
    val matchId: String,
    @ColumnInfo(name = "player_id")
    val playerId: String,
    @ColumnInfo(name = "victory")
    val victory: Boolean,
    @ColumnInfo(name = "xp_earned")
    val xpEarned: Int,
    @ColumnInfo(name = "played_at")
    val playedAt: Long,
    @ColumnInfo(name = "summary")
    val summary: String?,
)