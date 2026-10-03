package com.cardclash.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cardclash.data.local.entity.DeckCardEntity
import com.cardclash.data.local.entity.DeckEntity
import com.cardclash.data.local.model.DeckWithCards

/**
 * Acceso a mazos y cartas de mazo (`decks`, `deck_cards`).
 *
 * Los metodos suspend usan corrutinas de Room. Las operaciones multi-tabla
 * (save/update/delete con cartas) se envuelven en transacciones en el
 * repositorio ([PlayerProgressRepository]) para atomicidad.
 */
@Dao
interface DeckDao {

    // ------------------------------------------------------------------
    // Escritura: DeckEntity
    // ------------------------------------------------------------------

    /** Inserta un mazo; devuelve el rowId de SQLite (no el deckId UUID). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: DeckEntity): Long

    /** Actualiza un mazo (por deck_id PK); devuelve filas afectadas (0/1). */
    @Update
    suspend fun updateDeck(deck: DeckEntity): Int

    /** Elimina un mazo por deck_id; devuelve filas afectadas (0/1). */
    @Query("DELETE FROM decks WHERE deck_id = :deckId")
    suspend fun deleteDeck(deckId: String): Int

    // ------------------------------------------------------------------
    // Escritura: DeckCardEntity (batch)
    // ------------------------------------------------------------------

    /** Inserta/reemplaza las cartas de un mazo en batch. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeckCards(cards: List<DeckCardEntity>)

    /**
     * Reemplaza todas las cartas de un mazo: borra las existentes e inserta
     * las nuevas. Usar dentro de transaccion con updateDeck.
     */
    @Transaction
    suspend fun updateDeckCards(deckId: String, cards: List<DeckCardEntity>) {
        deleteDeckCards(deckId)
        if (cards.isNotEmpty()) {
            insertDeckCards(cards)
        }
    }

    /** Elimina todas las cartas de un mazo; devuelve filas borradas. */
    @Query("DELETE FROM deck_cards WHERE deck_id = :deckId")
    suspend fun deleteDeckCards(deckId: String): Int

    // ------------------------------------------------------------------
    // Lectura simple
    // ------------------------------------------------------------------

    /** Un mazo por deck_id, o null si no existe. */
    @Query("SELECT * FROM decks WHERE deck_id = :deckId")
    suspend fun getDeck(deckId: String): DeckEntity?

    /**
     * Todos los mazos de un jugador ordenados: activo primero, luego por
     * updated_at descendente (mas reciente primero).
     */
    @Query(
        """
        SELECT * FROM decks
        WHERE player_id = :playerId
        ORDER BY is_active DESC, updated_at DESC
        """,
    )
    suspend fun getAllDecks(playerId: String): List<DeckEntity>

    /** Mazo activo del jugador (is_active = true), o null si no hay ninguno. */
    @Query(
        """
        SELECT * FROM decks
        WHERE player_id = :playerId AND is_active = 1
        LIMIT 1
        """,
    )
    suspend fun getActiveDeck(playerId: String): DeckEntity?

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /**
     * Verifica si existe un mazo con [name] para [playerId], excluyendo
     * opcionalmente [excludeId] (util al renombrar).
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM decks
            WHERE player_id = :playerId AND name = :name
            AND (:excludeId IS NULL OR deck_id != :excludeId)
        )
        """,
    )
    suspend fun existsName(playerId: String, name: String, excludeId: String?): Boolean

    /**
     * Activa un mazo y desactiva los demas del mismo jugador en una sola
     * transaccion atomica (2 UPDATEs). Devuelve filas afectadas total.
     */
    @Transaction
    suspend fun setActiveDeck(playerId: String, deckId: String, now: Long): Int {
        val cleared = clearActiveDeck(playerId, now)
        val set = activateDeck(deckId, now)
        return cleared + set
    }

    /** Desactiva todos los mazos del jugador (is_active = false). */
    @Query("UPDATE decks SET is_active = 0, updated_at = :now WHERE player_id = :playerId AND is_active = 1")
    suspend fun clearActiveDeck(playerId: String, now: Long): Int

    /** Activa un mazo concreto (is_active = true). */
    @Query("UPDATE decks SET is_active = 1, updated_at = :now WHERE deck_id = :deckId")
    suspend fun activateDeck(deckId: String, now: Long): Int

    // ------------------------------------------------------------------
    // Lectura compuesta (POJO @Relation)
    // ------------------------------------------------------------------

    /**
     * Mazo completo con sus cartas ordenadas por slot (0..8).
     * Room rellena automaticamente la lista [cards] via @Relation.
     */
    @Transaction
    @Query("SELECT * FROM decks WHERE deck_id = :deckId")
    suspend fun getDeckWithCards(deckId: String): DeckWithCards?
}