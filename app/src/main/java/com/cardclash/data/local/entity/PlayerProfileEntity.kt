package com.cardclash.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tabla de progresion del jugador (`player_profile`), fila unica.
 *
 * Persiste SOLO el XP total acumulado ([xpTotal]) como fuente de verdad.
 * El nivel, el tramo y el limite de copias se DERIVAN en memoria con
 * [com.cardclash.domain.progression.LevelCurve] / [com.cardclash.domain.progression.ProgressionRules]
 * via [com.cardclash.data.local.mapper.PlayerProgressMapper]: NO se persisten
 * como columnas derivadas para evitar desincronizacion con el XP acumulado.
 */
@Entity(tableName = "player_profile")
data class PlayerProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "player_id")
    val playerId: String,
    @ColumnInfo(name = "xp_total")
    val xpTotal: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        /** Identificador fijo de la fila unica de perfil local. */
        const val DEFAULT_PLAYER_ID = "local-player"
    }
}