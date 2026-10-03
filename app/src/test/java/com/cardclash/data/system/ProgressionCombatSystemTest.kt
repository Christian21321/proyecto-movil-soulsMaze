package com.cardclash.data.system

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cardclash.data.LocalMatchResultSink
import com.cardclash.data.PlayerProgressRepository
import com.cardclash.data.battle.NoOpMatchSessionGateway
import com.cardclash.data.local.AppDatabase
import com.cardclash.domain.battle.MatchSessionController
import com.cardclash.domain.battle.SessionStatus
import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.service.DeckRuleService
import com.cardclash.ui.battle.DeckBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests de SISTEMA (Grupo B): progresion + combate end-to-end con la pila
 * LOCAL real (Room in-memory + [PlayerProgressRepository] + [LocalMatchResultSink]
 * + [MatchSessionController] con el gateway NO-OP, canal local demo).
 *
 * Es el flujo que usa el frontend para la demo visible: sembrar la coleccion,
 * construir un mazo legal, jugar la partida local hasta FINISHED y comprobar
 * que el desenlace quedo persistido en la base (XP + historial) en la misma
 * transaccion.
 *
 * Robolectric SDK 35 (targetSdk 37 supera el maxSdkVersion de Robolectric 4.16.1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgressionCombatSystemTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: PlayerProgressRepository
    private lateinit var sink: LocalMatchResultSink

    private val me: PlayerId get() = PlayerId("ME")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlayerProgressRepository(db = db, catalog = DefaultCardCatalog())
        sink = LocalMatchResultSink(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Controlador de la demo local con el sumidero REAL (persiste en Room). */
    private fun controller(scope: CoroutineScope): MatchSessionController =
        MatchSessionController(
            myId = me,
            engine = MatchSessionController.demoEngine(),
            catalog = DefaultCardCatalog(),
            gateway = NoOpMatchSessionGateway(),
            resultSink = sink,
            scope = scope,
        )

    /** Mazo exclusivamente pasivo: el BOT no puede poblar ni atacar. */
    private fun botPassiveDeck(): List<CardId> = List(12) { CardId("passive-max_mana-+2") }

    /** Mazo agresivo de ataques baratos y altos. */
    private fun aggroBotDeck(): List<CardId> = List(12) { CardId("attack-5") }

    // ------------------------------------------------------------------
    // Flujo victoria (coleccion sembrada -> deck legal -> partida -> BD)
    // ------------------------------------------------------------------

    @Test
    fun flujoCompleto_victoria_coleccionSembrada_construyeDeckLegal_terminaYRegistraEnBD() = runBlocking {
        // Sembrar la coleccion con cartas del catalogo real (1 copia de cada).
        val seeded = listOf(
            "attack-0", "attack-1", "attack-2", "attack-3", "attack-4", "attack-5",
            "heal-0", "draw-0", "passive-max_mana-+2",
        )
        seeded.forEach { repository.addCardCopy(CardId(it)) }

        val owned = repository.ownedCollection()
        assertEquals(seeded.size, owned.ownedCards().size)

        // Deck legal construido desde lo poseido y validado por las reglas.
        val catalog = DefaultCardCatalog()
        val deck = DeckBuilder.buildDeck(owned.ownedCards(), catalog)
        assertTrue("el deck construido debe ser legal", DeckRuleService(catalog).validate(deck).isOk)

        // Partida local: mi deck legal vs BOT pasivo (nunca puebla -> pierde).
        val ctl = controller(this)
        ctl.startLocalDemo(myDeck = deck, opponentDeck = botPassiveDeck())

        for (iter in 0 until 40) {
            val ui = ctl.state.value.uiState ?: break
            if (ctl.state.value.status != SessionStatus.ACTIVE) break
            if (!ui.isMyTurn) {
                delay(1)
                continue
            }
            val playable = ui.myHand.filter { it.playable }
            if (playable.isNotEmpty()) {
                // Los ataques del mazo legal apuntan al avatar rival por defecto.
                ctl.playCard(playable.first().instanceId, null)
            } else {
                ctl.endTurn()
            }
        }
        // Deja que el lanzamiento del sumidero (async) se ejecute.
        delay(50)

        assertEquals(SessionStatus.FINISHED, ctl.state.value.status)
        val history = repository.matchHistory(10)
        assertEquals(1, history.size)
        assertTrue(history.single().victory)
        assertEquals(5, history.single().xpEarned)
        // La victoria persistio +5 XP (fuente de verdad en BD).
        assertEquals(5, repository.currentProgress().xpTotal)
    }

    // ------------------------------------------------------------------
    // Flujo derrota (mazo pasivo local vs BOT agresivo)
    // ------------------------------------------------------------------

    @Test
    fun flujoCompleto_derrota_otorga2() = runBlocking {
        // El jugador local parte con un mazo exclusivamente pasivo (max_mana),
        // que nunca daña el avatar del BOT; el BOT agresivo ataca el avatar local
        // hasta reducirlo a 0, lo que declara OPPONENT_DEFEATED de forma determinista.
        val ctl = controller(this)
        ctl.startLocalDemo(myDeck = botPassiveDeck(), opponentDeck = aggroBotDeck())

        // El local no tiene ataques: solo cede su turno mientras el BOT lo aniquila.
        var guard = 0
        while (ctl.state.value.status == SessionStatus.ACTIVE && guard++ < 200) {
            val ui = ctl.state.value.uiState ?: break
            if (!ui.isMyTurn) { delay(1); continue }
            ctl.endTurn()
        }
        delay(50)

        assertEquals(SessionStatus.FINISHED, ctl.state.value.status)
        assertFalse(ctl.state.value.uiState!!.isVictory)

        val history = repository.matchHistory(10)
        assertEquals(1, history.size)
        assertFalse(history.single().victory)
        assertEquals(2, history.single().xpEarned)
        assertEquals(2, repository.currentProgress().xpTotal)
    }
}