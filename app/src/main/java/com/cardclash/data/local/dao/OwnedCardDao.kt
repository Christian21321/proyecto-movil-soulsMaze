package com.cardclash.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.cardclash.data.local.entity.OwnedCardEntity

/**
 * Acceso a la coleccion de cartas (`owned_cards`).
 *
 * Los DAO son "tienda tonta": NO validan reglas de negocio. La validacion del
 * limite de copias contra [com.cardclash.domain.progression.ProgressionRules]
 * ocurre en [com.cardclash.data.PlayerProgressRepository], que combina dominio
 * + DB dentro de una transaccion ([com.cardclash.data.local.AppDatabase]).
 *
 * [increment] y [decrement] son operaciones SQL atomicas (una sola sentencia).
 */
@Dao
interface OwnedCardDao {

    /** Todas las cartas poseidas (para reconstruir la coleccion completa). */
    @Query("SELECT * FROM owned_cards")
    suspend fun getAll(): List<OwnedCardEntity>

    /** Copias de una carta, o null si la carta no esta en la coleccion. */
    @Query("SELECT owned_count FROM owned_cards WHERE card_id = :cardId")
    suspend fun countOf(cardId: String): Int?

    /**
     * Anade 1 copia de forma atomica: inserta con 1 si no existe, o incrementa
     * en 1 si ya existe (UPSERT). El llamador debe haber validado el limite.
     */
    @Query(
        """
        INSERT INTO owned_cards (card_id, owned_count) VALUES (:cardId, 1)
        ON CONFLICT(card_id) DO UPDATE SET owned_count = owned_count + 1
        """,
    )
    suspend fun increment(cardId: String)

    /**
     * Resta 1 copia (devuelve filas afectadas; 0 si no habia copias).
     * El llamador decide si elimina la fila cuando la cuenta llega a 0.
     */
    @Query("UPDATE owned_cards SET owned_count = owned_count - 1 WHERE card_id = :cardId AND owned_count > 0")
    suspend fun decrement(cardId: String): Int

    /** Elimina la fila (cuando la ultima copia se elimina). */
    @Query("DELETE FROM owned_cards WHERE card_id = :cardId")
    suspend fun delete(cardId: String)

    /** Vacia la tabla (reset local; util en tests/instrumentados). */
    @Query("DELETE FROM owned_cards")
    suspend fun clear()
}