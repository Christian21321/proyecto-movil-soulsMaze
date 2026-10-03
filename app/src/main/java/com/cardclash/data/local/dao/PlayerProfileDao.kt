package com.cardclash.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cardclash.data.local.entity.PlayerProfileEntity

/**
 * Acceso a la progresion del jugador (`player_profile`), fila unica.
 *
 * Las operaciones de escritura del XP total las orquesta
 * [com.cardclash.data.PlayerProgressRepository] dentro de transacciones
 * (deriva el nuevo XP con [com.cardclash.domain.progression.ProgressionRules]
 * y hace upsert de la fila unica).
 */
@Dao
interface PlayerProfileDao {

    /** La fila unica de perfil, o null si el jugador no tiene progreso aun. */
    @Query("SELECT * FROM player_profile WHERE player_id = :playerId")
    suspend fun get(playerId: String): PlayerProfileEntity?

    /** XP total acumulado, o null si no hay fila. */
    @Query("SELECT xp_total FROM player_profile WHERE player_id = :playerId")
    suspend fun getXpTotal(playerId: String): Int?

    /**
     * Inserta o actualiza la fila unica ([PlayerProfileEntity.DEFAULT_PLAYER_ID]).
     * Usa @Upsert (UPDATE sin borrar) y NO OnConflictStrategy.REPLACE: REPLACE
     * hace DELETE + INSERT del padre, y la FK de `match_history` con
     * onDelete = CASCADE borraria todo el historial del jugador en cada
     * escritura de XP (perdida de datos). @Upsert preserva la fila y el
     * historial acumulado.
     */
    @Upsert
    suspend fun upsert(entity: PlayerProfileEntity)

    /** Vacia la tabla (reset local; util en tests/instrumentados). */
    @Query("DELETE FROM player_profile")
    suspend fun clear()
}