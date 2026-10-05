package com.cardclash.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cardclash.data.local.AppDatabase
import com.cardclash.data.local.entity.PlayerProfileEntity
import com.cardclash.data.local.logic.CardCollectionOps
import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.progression.ProgressionRules
import com.cardclash.domain.progression.Tier
import com.cardclash.domain.progression.XpSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests de INTEGRACION del [PlayerProgressRepository] con Room real y el
 * dominio REAL (catalogo y reglas de progresion), Grupo B (P0).
 *
 * Verifica que el repositorio: (1) deriva la progresion SOLO desde el XP
 * persistido; (2) valida el limite de copias contra el nivel derivado; (3)
 * registra partidas otorgando XP y escribiendo historial en la MISMA
 * transaccion; y (4) deja intacta la base cuando la operacion es invalida
 * (fuera de rango, carta desconocida, copia no poseida).
 *
 * El clock es controlable para ordenar el historial por fecha de forma
 * determinista. Cada test usa una base in-memory aislada (Robolectric SDK 35).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerProgressRepositoryIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PlayerProgressRepository
    private val catalog = DefaultCardCatalog()
    private val rules = ProgressionRules()
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        now = 1_000L
        repo = PlayerProgressRepository(
            db = db,
            catalog = catalog,
            rules = rules,
            clock = { ++now },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Siembra XP total directamente en la fila unica (fuente de verdad). */
    private suspend fun seedXp(xp: Int) {
        db.playerProfileDao().upsert(
            PlayerProfileEntity(PlayerProfileEntity.DEFAULT_PLAYER_ID, xp, ++now),
        )
    }

    // ------------------------------------------------------------------
    // Progresion
    // ------------------------------------------------------------------

    @Test
    fun currentProgress_sinFila_devuelveEstadoInicialSinPersistir() = runBlocking {
        val p = repo.currentProgress()
        assertEquals(0, p.xpTotal)
        assertEquals(1, p.level)
        assertEquals(Tier.T1, p.tier)
        assertEquals(2, p.maxCopiesAllowed)
        // No crea fila: sigue sin existir en BD.
        assertNull(db.playerProfileDao().get(PlayerProfileEntity.DEFAULT_PLAYER_ID))
    }

    @Test
    fun grantXp_victoria_persisteYDeriva() = runBlocking {
        val p = repo.grantXp(XpSource.VICTORY)
        assertEquals(5, p.xpTotal)
        assertEquals(1, p.level)
        assertEquals(5, p.xpIntoLevel)
        // Persistido en la fila unica.
        assertEquals(5, db.playerProfileDao().getXpTotal(PlayerProfileEntity.DEFAULT_PLAYER_ID))
    }

    @Test
    fun grantXp_defeat_persiste() = runBlocking {
        val p = repo.grantXp(XpSource.DEFEAT)
        assertEquals(2, p.xpTotal)
        assertEquals(2, db.playerProfileDao().getXpTotal(PlayerProfileEntity.DEFAULT_PLAYER_ID))
    }

    @Test
    fun grantShopXp_fueraDeRango_noTocaBD_yFalla() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.grantShopXp(3) }
        }
        runBlocking {
            // La transaccion no persistio nada: XP intacto y sin fila de perfil.
            assertEquals(0, repo.currentProgress().xpTotal)
            assertNull(db.playerProfileDao().get(PlayerProfileEntity.DEFAULT_PLAYER_ID))
        }
    }

    // ------------------------------------------------------------------
    // Coleccion de cartas
    // ------------------------------------------------------------------

    @Test
    fun addCardCopy_cartaDesconocida_devuelveUnknownCard_sinEscribir() = runBlocking {
        val outcome = repo.addCardCopy(CardId("no-existe"))
        assertTrue(outcome is CardCollectionOps.AddCopyOutcome.UnknownCard)
        assertNull(db.ownedCardDao().countOf("no-existe"))
        assertTrue(repo.ownedCollection().ownedCards().isEmpty())
    }

    @Test
    fun addCardCopy_hastaElLimiteDelNivel_acumula() = runBlocking {
        val cardId = CardId("attack-0")
        val first = repo.addCardCopy(cardId)
        val second = repo.addCardCopy(cardId)
        // T1 (nivel 1): limite 2 -> ambas copias caben.
        assertEquals(CardCollectionOps.AddCopyOutcome.Added(1), first)
        assertEquals(CardCollectionOps.AddCopyOutcome.Added(2), second)
        assertEquals(2, repo.ownedCount(cardId))
        assertEquals(2, db.ownedCardDao().countOf("attack-0"))
    }

    @Test
    fun addCardCopy_porEncimaDelLimite_devuelveLimitReached_yNoIncrementaBD() = runBlocking {
        val cardId = CardId("attack-0")
        repo.addCardCopy(cardId)
        repo.addCardCopy(cardId)
        val third = repo.addCardCopy(cardId)
        assertEquals(CardCollectionOps.AddCopyOutcome.LimitReached(2, 2), third)
        // La BD no cambio: sigue con 2 copias.
        assertEquals(2, repo.ownedCount(cardId))
        assertEquals(2, db.ownedCardDao().countOf("attack-0"))
    }

    @Test
    fun addCardCopy_cuandoSubeNivel_desbloqueaMasCopias() = runBlocking {
        // XP 2499 = nivel 40 (T1, limite 2); 2500+ = nivel 41 (T2, limite 3).
        seedXp(2499)
        assertEquals(40, repo.currentProgress().level)
        assertEquals(2, repo.currentProgress().maxCopiesAllowed)

        val cardId = CardId("attack-0")
        repo.addCardCopy(cardId)
        repo.addCardCopy(cardId)
        assertEquals(CardCollectionOps.AddCopyOutcome.LimitReached(2, 2), repo.addCardCopy(cardId))

        // Gano una victoria: 2504 XP -> nivel 41 -> limite 3 desbloqueado.
        val afterWin = repo.grantXp(XpSource.VICTORY)
        assertEquals(41, afterWin.level)
        assertEquals(3, afterWin.maxCopiesAllowed)

        val third = repo.addCardCopy(cardId)
        assertEquals(CardCollectionOps.AddCopyOutcome.Added(3), third)
        assertEquals(3, repo.ownedCount(cardId))
    }

    @Test
    fun removeCardCopy_hasta0_eliminaFila() = runBlocking {
        val cardId = CardId("attack-0")
        repo.addCardCopy(cardId)
        repo.addCardCopy(cardId)

        assertEquals(CardCollectionOps.RemoveCopyOutcome.Removed(1), repo.removeCardCopy(cardId))
        assertEquals(1, repo.ownedCount(cardId))

        val last = repo.removeCardCopy(cardId)
        assertEquals(CardCollectionOps.RemoveCopyOutcome.Removed(0), last)
        // 0 copias = carta no poseida: la fila se elimina de Room.
        assertEquals(0, repo.ownedCount(cardId))
        assertNull(db.ownedCardDao().countOf("attack-0"))
    }

    @Test
    fun removeCardCopy_noPoseida_devuelveNotOwned_sinEscribir() = runBlocking {
        val outcome = repo.removeCardCopy(CardId("attack-0"))
        assertTrue(outcome is CardCollectionOps.RemoveCopyOutcome.NotOwned)
        assertNull(db.ownedCardDao().countOf("attack-0"))
    }

    // ------------------------------------------------------------------
    // Historial de partidas (XP + historial en la misma transaccion)
    // ------------------------------------------------------------------

    @Test
    fun recordMatch_victoria_otorga5XpYEscribeHistorialEnMismaTransaccion() = runBlocking {
        val m1 = MatchId("m1")
        val summary = repo.recordMatch(m1, victory = true, summary = "Victoria por OPPONENT_DEFEATED")

        assertEquals(5, summary.xpEarned)
        assertTrue(summary.victory)
        // Ambos efectos visibles tras la llamada (misma transaccion).
        assertEquals(5, repo.currentProgress().xpTotal)
        val history = repo.matchHistory(10)
        assertEquals(1, history.size)
        assertEquals(m1, history.single().matchId)
        assertEquals(true, history.single().victory)
    }

    @Test
    fun recordMatch_defeat_otorga2XpYEscribeHistorial() = runBlocking {
        val m1 = MatchId("m1")
        val summary = repo.recordMatch(m1, victory = false, summary = "Derrota por OPPONENT_DEFEATED")

        assertEquals(2, summary.xpEarned)
        assertEquals(2, repo.currentProgress().xpTotal)
        val history = repo.matchHistory(10)
        assertEquals(1, history.size)
        assertEquals(false, history.single().victory)
        assertEquals(2, history.single().xpEarned)
    }

    @Test
    fun recordMatch_consistenciaHistorico() = runBlocking {
        repo.recordMatch(MatchId("m1"), victory = true)
        repo.recordMatch(MatchId("m2"), victory = false)
        repo.recordMatch(MatchId("m3"), victory = false)

        val history = repo.matchHistory(10)
        assertEquals(3, history.size)
        // El clock controlable incrementa: orden mas nueva primero.
        assertEquals(listOf("m3", "m2", "m1"), history.map { it.matchId.value })
        // XP acumulado = 5 + 2 + 2 = 9, coherente con lo persistido.
        assertEquals(9, history.sumOf { it.xpEarned })
        assertEquals(9, repo.currentProgress().xpTotal)
    }
}