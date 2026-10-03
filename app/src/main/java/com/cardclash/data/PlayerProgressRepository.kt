package com.cardclash.data

import androidx.room.withTransaction
import com.cardclash.data.local.AppDatabase
import com.cardclash.data.local.entity.DeckCardEntity
import com.cardclash.data.local.entity.DeckEntity
import com.cardclash.data.local.entity.PlayerProfileEntity
import com.cardclash.data.local.logic.CardCollectionOps
import com.cardclash.data.local.mapper.DeckMapper
import com.cardclash.data.local.mapper.MatchHistoryMapper
import com.cardclash.data.local.mapper.OwnedCardMapper
import com.cardclash.data.local.mapper.PlayerProgressMapper
import com.cardclash.data.local.model.Deck
import com.cardclash.data.local.model.DeckCard
import com.cardclash.data.local.model.DeckId
import com.cardclash.data.local.model.MatchSummary
import com.cardclash.data.local.model.PlayerProgress
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.progression.CardCollection
import com.cardclash.domain.progression.ProgressionRules
import com.cardclash.domain.progression.XpSource
import com.cardclash.domain.repository.CardCatalog

/**
 * Errores del repositorio local, envueltos en [Result.Failure].
 */
sealed class RepositoryError(override val message: String) : Exception(message) {
    /** Error de almacenamiento (SQL, IO, constraints). */
    data class Storage(
        override val cause: Throwable,
    ) : RepositoryError("Storage error: ${cause.message}")

    /** Recurso no encontrado (deck, perfil, etc.). */
    data class NotFound(val detail: String) : RepositoryError("Not found: $detail")

    /** Violacion de unicidad (nombre de mazo duplicado, etc.). */
    data class DuplicateName(val detail: String) : RepositoryError("Duplicate name: $detail")

    /** Carta desconocida en el catalogo. */
    data class UnknownCard(val detail: String) : RepositoryError("Unknown card: $detail")

    /** Validacion de reglas de negocio fallida. */
    data class Validation(val detail: String) : RepositoryError("Validation failed: $detail")
}

/**
 * Repositorio local de progresion, coleccion, historial y MAZOS de CardClash.
 *
 * Orquesta el dominio (reglas PURAS: [ProgressionRules], [CardCollection]) con
 * Room ([AppDatabase] y sus DAO). NO mezcla con la capa remota
 * (`data.remote`): la persistencia local es independiente de Firestore.
 *
 * ## Reglas de coherencia
 * - El XP total es la unica fuente de verdad persistida; nivel/tramo/limite se
 *   derivan en memoria ([PlayerProgressMapper.deriveProgress]).
 * - Anadir copias valida el limite contra [ProgressionRules.maxCopiesAllowed]
 *   con el nivel derivado del XP actual; la carta debe existir en [catalog].
 * - Las operaciones multi-paso usan transacciones atomicas
 *   ([androidx.room.withTransaction]): por ejemplo, registrar una partida
 *   otorga XP y escribe el historial en la misma transaccion.
 * - Mazos: save/delete/setActive usan transacciones para atomicidad deck + cartas.
 *   El nombre es unico por jugador (validado antes de escribir).
 *
 * La logica de validacion/transformacion que no toca Room vive en funciones
 * puras ([CardCollectionOps], mappers) y se testea con unit tests JVM.
 */
class PlayerProgressRepository(
    private val db: AppDatabase,
    private val catalog: CardCatalog,
    private val rules: ProgressionRules = ProgressionRules(),
    private val playerId: PlayerId = PlayerId(PlayerProfileEntity.DEFAULT_PLAYER_ID),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    // ------------------------------------------------------------------
    // Progresion
    // ------------------------------------------------------------------

    /**
     * Progresion derivada del XP total persistido. Si aun no hay fila de
     * perfil, devuelve el estado inicial (XP 0, nivel 1) sin persistirlo.
     */
    suspend fun currentProgress(): PlayerProgress {
        val profile = loadProfile()
        return PlayerProgressMapper.toProgress(profile, rules)
    }

    /** Otorga XP de una fuente fija (victoria/derrota/tienda-maxima) y persiste. */
    suspend fun grantXp(source: XpSource): PlayerProgress = db.withTransaction {
        val profile = loadProfile()
        val newXp = rules.grantXp(profile.xpTotal, source)
        persistXp(newXp)
    }

    /**
     * Otorga XP de tienda ([amount] en 0..2, tope validado por
     * [ProgressionRules.grantShopXp]) y persiste.
     */
    suspend fun grantShopXp(amount: Int): PlayerProgress = db.withTransaction {
        val profile = loadProfile()
        val newXp = rules.grantShopXp(profile.xpTotal, amount)
        persistXp(newXp)
    }

    // ------------------------------------------------------------------
    // Coleccion de cartas
    // ------------------------------------------------------------------

    /** Coleccion inmutable completa reconstruida desde Room. */
    suspend fun ownedCollection(): CardCollection =
        OwnedCardMapper.toCollection(db.ownedCardDao().getAll())

    /** Copias poseidas de [cardId] (0 si no se posee). */
    suspend fun ownedCount(cardId: CardId): Int =
        db.ownedCardDao().countOf(cardId.value) ?: 0

    /**
     * Anade una copia de [cardId] validando: (1) la carta existe en el
     * catalogo; (2) el limite de copias del nivel derivado del XP actual.
     * Operacion atomica (transaccion).
     */
    suspend fun addCardCopy(cardId: CardId): CardCollectionOps.AddCopyOutcome = db.withTransaction {
        if (catalog.findById(cardId) == null) {
            CardCollectionOps.AddCopyOutcome.UnknownCard(cardId)
        } else {
            val progress = currentProgress()
            val current = db.ownedCardDao().countOf(cardId.value) ?: 0
            val outcome = CardCollectionOps.nextCountAfterAdd(current, progress.level, rules)
            when (outcome) {
                is CardCollectionOps.AddCopyOutcome.Added -> db.ownedCardDao().increment(cardId.value)
                else -> Unit
            }
            outcome
        }
    }

    /**
     * Elimina una copia de [cardId]. Si llegaba a 0 copias se elimina la fila
     * (coherente con [CardCollection], donde 0 copias = carta no poseida).
     * Operacion atomica (transaccion).
     */
    suspend fun removeCardCopy(cardId: CardId): CardCollectionOps.RemoveCopyOutcome = db.withTransaction {
        val current = db.ownedCardDao().countOf(cardId.value) ?: 0
        val outcome = CardCollectionOps.nextCountAfterRemove(current)
        when (outcome) {
            is CardCollectionOps.RemoveCopyOutcome.Removed ->
                if (outcome.newCount == 0) {
                    db.ownedCardDao().delete(cardId.value)
                } else {
                    db.ownedCardDao().decrement(cardId.value)
                }
            is CardCollectionOps.RemoveCopyOutcome.NotOwned -> Unit
        }
        outcome
    }

    // ------------------------------------------------------------------
    // Historial de partidas
    // ------------------------------------------------------------------

    /**
     * Registra una partida en el historial y otorga el XP correspondiente en la
     * MISMA transaccion (+5 victoria, +2 derrota). Devuelve el resumen.
     */
    suspend fun recordMatch(
        matchId: MatchId,
        victory: Boolean,
        summary: String? = null,
    ): MatchSummary = db.withTransaction {
        val profile = loadProfile()
        val source = if (victory) XpSource.VICTORY else XpSource.DEFEAT
        val xpEarned = source.rewardXp
        val now = clock()
        db.playerProfileDao().upsert(PlayerProfileEntity(playerId.value, profile.xpTotal + xpEarned, now))
        val entity = MatchHistoryMapper.toEntity(matchId, playerId, victory, xpEarned, now, summary)
        db.matchHistoryDao().insert(entity)
        MatchHistoryMapper.toSummary(entity)
    }

    /** Las [limit] partidas mas recientes del jugador (mas nueva primero). */
    suspend fun matchHistory(limit: Int = 20): List<MatchSummary> =
        db.matchHistoryDao().recent(playerId.value, limit).map { MatchHistoryMapper.toSummary(it) }

    // ------------------------------------------------------------------
    // Mazos (Deck)
    // ------------------------------------------------------------------

    /**
     * Guarda un mazo completo (metadata + cartas) en una transaccion atomica.
     * Valida: (1) nombre unico para el jugador; (2) cartas existen en catalogo;
     * (3) max 9 cartas (slots 0-8); (4) sin slots duplicados.
     * Si [deck.id] es nuevo, inserta; si existe, actualiza (upsert semantico).
     */
    suspend fun saveDeck(deck: Deck): Result<Deck> = db.withTransaction {
        try {
            // Validar nombre unico (excluyendo el propio deck si es actualizacion)
            val excludeId = if (deck.id.value.isNotEmpty()) deck.id.value else null
            if (db.deckDao().existsName(playerId.value, deck.name, excludeId)) {
                return@withTransaction Result.failure(
                    RepositoryError.DuplicateName("Ya existe un mazo con el nombre '${deck.name}'")
                )
            }

            // Validar que todas las cartas existen en el catalogo
            val unknownCards = deck.cards.filter { catalog.findById(it.cardId) == null }
            if (unknownCards.isNotEmpty()) {
                return@withTransaction Result.failure(
                    RepositoryError.UnknownCard("Cartas desconocidas en el catalogo: ${unknownCards.map { it.cardId.value }}")
                )
            }

            val (entity, cardEntities) = DeckMapper.toEntity(deck)
            val now = clock()
            val entityToSave = entity.copy(updatedAt = now)

            if (db.deckDao().getDeck(entity.id) != null) {
                // Actualizar mazo existente
                db.deckDao().updateDeck(entityToSave)
                db.deckDao().updateDeckCards(entity.id, cardEntities)
            } else {
                // Insertar nuevo mazo
                db.deckDao().insertDeck(entityToSave)
                if (cardEntities.isNotEmpty()) {
                    db.deckDao().insertDeckCards(cardEntities)
                }
            }

            // Recargar con cartas para devolver el estado completo
            val saved = db.deckDao().getDeckWithCards(entity.id)
                ?: return@withTransaction Result.failure(RepositoryError.NotFound("Deck no encontrado tras guardar"))
            Result.success(DeckMapper.toDomain(saved))
        } catch (e: Exception) {
            Result.failure(RepositoryError.Storage(e))
        }
    }

    /**
     * Carga todos los mazos del jugador (metadata + cartas).
     * Orden: activo primero, luego por updated_at descendente.
     */
    suspend fun loadDecks(): Result<List<Deck>> = db.withTransaction {
        try {
            val decksWithCards = db.deckDao().getAllDecks(playerId.value).mapNotNull { entity ->
                val cards = db.deckDao().getDeckWithCards(entity.id)?.cards ?: emptyList()
                DeckMapper.toDomain(entity, cards)
            }
            Result.success(decksWithCards)
        } catch (e: Exception) {
            Result.failure(RepositoryError.Storage(e))
        }
    }

    /**
     * Carga el mazo activo del jugador (metadata + cartas), o null si no hay.
     */
    suspend fun loadActiveDeck(): Result<Deck?> = db.withTransaction {
        try {
            val active = db.deckDao().getActiveDeck(playerId.value)
            if (active == null) return@withTransaction Result.success(null)
            val withCards = db.deckDao().getDeckWithCards(active.id)
            Result.success(withCards?.let { DeckMapper.toDomain(it) })
        } catch (e: Exception) {
            Result.failure(RepositoryError.Storage(e))
        }
    }

    /**
     * Establece [deckId] como mazo activo del jugador (desactiva los demas).
     * Operacion atomica: clearActiveDeck + activateDeck en misma transaccion.
     */
    suspend fun setActiveDeck(deckId: DeckId): Result<Unit> = db.withTransaction {
        try {
            // Verificar que el mazo existe y pertenece al jugador
            val deck = db.deckDao().getDeck(deckId.value)
            if (deck == null || deck.playerId != playerId.value) {
                return@withTransaction Result.failure(RepositoryError.NotFound("Mazo no encontrado o no pertenece al jugador"))
            }
            val now = clock()
            db.deckDao().clearActiveDeck(playerId.value, now)
            db.deckDao().activateDeck(deckId.value, now)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(RepositoryError.Storage(e))
        }
    }

    /**
     * Elimina un mazo y todas sus cartas (CASCADE via FK).
     * Operacion atomica en transaccion.
     */
    suspend fun deleteDeck(deckId: DeckId): Result<Unit> = db.withTransaction {
        try {
            val deck = db.deckDao().getDeck(deckId.value)
            if (deck == null || deck.playerId != playerId.value) {
                return@withTransaction Result.failure(RepositoryError.NotFound("Mazo no encontrado o no pertenece al jugador"))
            }
            db.deckDao().deleteDeck(deckId.value)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(RepositoryError.Storage(e))
        }
    }

    /**
     * Verifica si existe un mazo con [name] para el jugador actual.
     * [excludeId] permite excluir un mazo (util al renombrar).
     */
    suspend fun existsName(name: String, excludeId: DeckId?): Boolean =
        db.deckDao().existsName(playerId.value, name, excludeId?.value)

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    /** Fila de perfil existente, o el estado inicial (XP 0) si no existe. */
    private suspend fun loadProfile(): PlayerProfileEntity =
        db.playerProfileDao().get(playerId.value)
            ?: PlayerProfileEntity(playerId.value, xpTotal = 0, updatedAt = clock())

    /** Calcula la progresion derivada para el nuevo XP y hace upsert atomico. */
    private suspend fun persistXp(newXp: Int): PlayerProgress {
        val now = clock()
        db.playerProfileDao().upsert(PlayerProfileEntity(playerId.value, newXp, now))
        return PlayerProgressMapper.deriveProgress(playerId.value, newXp, now, rules)
    }
}