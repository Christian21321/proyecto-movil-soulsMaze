package com.cardclash.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cardclash.data.local.entity.MatchHistoryEntity

/**
 * Acceso al historial de partidas (`match_history`).
 *
 * [recent] devuelve las N ultimas partidas de un jugador ordenadas por fecha
 * descendente (usa el indice de [PlayerId] + [playedAt] de la entidad).
 */
@Dao
interface MatchHistoryDao {

    /** Inserta o reemplaza una partida; devuelve su rowid. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MatchHistoryEntity): Long

    /** Las [limit] partidas mas recientes del jugador (mas nueva primero). */
    @Query(
        """
        SELECT * FROM match_history
        WHERE player_id = :playerId
        ORDER BY played_at DESC, match_id DESC
        LIMIT :limit
        """,
    )
    suspend fun recent(playerId: String, limit: Int): List<MatchHistoryEntity>

    /** Vacia la tabla (reset local; util en tests/instrumentados). */
    @Query("DELETE FROM match_history")
    suspend fun clear()
}