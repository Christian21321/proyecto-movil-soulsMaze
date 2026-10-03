package com.cardclash.domain.battle

import com.cardclash.data.remote.dto.ClientProjectionWire
import com.cardclash.data.remote.sync.ActionPayload
import com.cardclash.data.remote.sync.ActionType
import com.cardclash.data.remote.sync.HostSyncEngine
import com.cardclash.data.remote.sync.HostSyncState
import com.cardclash.data.remote.sync.MatchAction
import com.cardclash.domain.engine.CombatEngine
import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.engine.SeededDiceRoller
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchResult
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.WinReason
import com.cardclash.domain.repository.CardCatalog
import com.cardclash.domain.service.MatchFactory
import java.io.Closeable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests end-to-end del [MatchSessionController] con FAKES del gateway y del
 * sumidero de resultados. Sin Firestore: ejercita el modo LOCAL demo completo
 * (victoria por daño al avatar rival), el modo HOST en línea (con gateway fake)
 * y el modo CLIENT (sin revelar la mano del rival).
 */
class MatchSessionControllerTest {

    private val me: PlayerId get() = PlayerId("ME")
    private val opponent: PlayerId get() = PlayerId("OPP")

    private fun catalog(): CardCatalog = DefaultCardCatalog()
    private fun engine(): CombatEngine = CombatEngine(DefaultCardCatalog(), SeededDiceRoller(42))

    /** Mazo agresivo (ataque barato y alto) para que la demo finalice rápido. */
    private fun aggroDeck(): List<CardId> = List(12) { CardId("attack-5") }

    /** Mazo barato de ataques para el jugador local (matan rápido). */
    private fun myAttackDeck(): List<CardId> = List(12) { CardId("attack-0") }

    /** Mazo exclusivamente pasivo para el BOT: no puede atacar (su pasiva de mana
     *  no daña el avatar rival) y pierde al caer su avatar a 0. */
    private fun botPassiveDeck(): List<CardId> = List(12) { CardId("passive-max_mana-+2") }

    private inner class Record(val matchId: MatchId, val victory: Boolean, val xp: Int, val summary: String)

    private inner class FakeSink : MatchResultSink {
        val records = mutableListOf<Record>()
        override suspend fun recordMatch(matchId: MatchId, victory: Boolean, xpEarned: Int, summary: String) {
            records += Record(matchId, victory, xpEarned, summary)
        }
    }

    private inner class FakeGateway(
        private val host: PlayerId,
        private val client: PlayerId,
    ) : MatchSessionGateway {
        var lastCreateDeck: List<CardId>? = null
        var lastJoinCode: String? = null
        var lastJoinDeck: List<CardId>? = null
        var hostStateListener: ((HostSyncState) -> Unit)? = null
        var hostActionListener: ((MatchAction) -> Unit)? = null
        var projectionListener: ((ClientProjectionWire) -> Unit)? = null
        var resultListener: ((MatchResult) -> Unit)? = null
        val enqueued = mutableListOf<MatchAction>()
        val published = mutableListOf<HostSyncState>()

        /** true: al encolar una acción se re-entrega al host para procesarla. */
        var rerouteToHost: Boolean = true

        override suspend fun createMatch(hostId: PlayerId, deck: List<CardId>): Result<MatchId> {
            lastCreateDeck = deck
            return Result.success(MatchId("M-online"))
        }

        override suspend fun joinMatch(code: String, playerId: PlayerId, deck: List<CardId>): Result<MatchId> {
            lastJoinCode = code
            lastJoinDeck = deck
            return Result.success(MatchId("M-client"))
        }

        override suspend fun enqueueAction(action: MatchAction): Result<Unit> {
            enqueued += action
            if (rerouteToHost) hostActionListener?.invoke(action)
            return Result.success(Unit)
        }

        override fun listenHostState(onHostState: (HostSyncState) -> Unit): Closeable {
            hostStateListener = onHostState
            return Closeable {}
        }

        override fun listenHostActions(onAction: (MatchAction) -> Unit): Closeable {
            hostActionListener = onAction
            return Closeable {}
        }

        override suspend fun publishHostSnapshot(matchId: MatchId, state: HostSyncState): Result<Unit> {
            published += state
            return Result.success(Unit)
        }

        override fun listenClientProjection(onProjection: (ClientProjectionWire) -> Unit): Closeable {
            projectionListener = onProjection
            return Closeable {}
        }

        override fun listenResults(onResult: (MatchResult) -> Unit): Closeable {
            resultListener = onResult
            return Closeable {}
        }
    }

    /** Crea el estado inicial de una partida completa para sembrar al host. */
    private fun initialHostState(deck: List<CardId>): HostSyncState {
        val eng = engine()
        val factory = MatchFactory(DefaultCardCatalog(), eng)
        val snapshot = factory.newMatch(
            matchId = MatchId("M-online"),
            players = listOf(me, opponent),
            deckByPlayer = mapOf(me to deck, opponent to aggroDeck()),
        )
        return HostSyncEngine(eng, hostPlayerId = me, clientPlayerId = opponent).initialState(snapshot)
    }

    // ------------------------------------------------------------------
    // Modo LOCAL demo (motor + sync local end-to-end)
    // ------------------------------------------------------------------

    @Test
    fun localDemo_completo_terminaConResultadoYRegistroEnSinkConXpCorrecto() = runBlocking {
        val sink = FakeSink()
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(
            myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = sink, scope = this,
        )
        // El jugador local ataca el avatar del BOT; el BOT, solo con pasivas de
        // mana (no daña el avatar local), termina perdiendo de forma determinista.
        ctl.startLocalDemo(myDeck = myAttackDeck(), opponentDeck = botPassiveDeck())

        // Tras iniciar, es mi turno y la partida está en curso.
        var ui = ctl.state.value.uiState!!
        assertTrue(ui.isMyTurn)
        assertFalse(ui.isFinished)

        var guard = 0
        while (ctl.state.value.status == SessionStatus.ACTIVE && guard++ < 120) {
            ui = ctl.state.value.uiState!!
            if (!ui.isMyTurn) { delay(1); continue }
            val playable = ui.myHand.filter { it.playable }
            if (playable.isNotEmpty()) {
                // Los ataques golpean el avatar del rival por defecto (sin objetivo).
                ctl.playCard(playable.first().instanceId, null)
            } else {
                ctl.endTurn()
            }
        }

        // Deja que el lanzamiento del sumidero (async) se ejecute.
        delay(10)

        // La sesión debe haber terminado.
        assertEquals(SessionStatus.FINISHED, ctl.state.value.status)
        ui = ctl.state.value.uiState!!
        assertTrue(ui.isFinished)

        // El resultado se registró UNA sola vez con la XP coherente con el desenlace.
        assertEquals(1, sink.records.size)
        val record = sink.records.single()
        val expectedXp = if (record.victory) 5 else 2
        assertEquals(expectedXp, record.xp)
        assertEquals(ui.matchId, record.matchId)
        // Coherencia: victory == (ganó "yo").
        assertEquals(record.victory, ctl.state.value.uiState!!.isVictory)
    }

    @Test
    fun localDemo_playCardNoJugable_marcaRechazoSinAvanzar() = runBlocking {
        val sink = FakeSink()
        val ctl = MatchSessionController(
            myId = me, engine = engine(), catalog = catalog(),
            gateway = FakeGateway(me, opponent), resultSink = sink, scope = this,
        )
        ctl.startLocalDemo(myDeck = aggroDeck(), opponentDeck = aggroDeck())

        val seqBefore = ctl.state.value.uiState!!.turn
        // Instancia inexistente: no debe procesarse ni cambiar el estado.
        ctl.playCard(InstanceId("no-existe"), null)
        assertTrue(ctl.state.value.rejectionLog.isNotEmpty())
        // El turno no cambió (no se procesó acción válida).
        assertEquals(seqBefore, ctl.state.value.uiState!!.turn)
    }

    // ------------------------------------------------------------------
    // Modo HOST en línea (gateway fake)
    // ------------------------------------------------------------------

    @Test
    fun hostOnline_crearEncolarProcesar_avanzaEstadoYPublica() = runBlocking {
        val sink = FakeSink()
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(
            myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = sink, scope = this,
        )
        ctl.hostMatch(aggroDeck())
        assertEquals(SessionStatus.ACTIVE, ctl.state.value.status)
        assertEquals(SessionRole.HOST, ctl.state.value.role)
        assertFalse(ctl.state.value.hasUi)

        val initial = initialHostState(aggroDeck())
        gateway.hostStateListener?.invoke(initial)
        assertTrue(ctl.state.value.hasUi)
        assertTrue(ctl.state.value.uiState!!.isMyTurn)

        // Comienza el turno del host: enqueueAction de BEGIN_TURN (seq 0).
        ctl.beginTurn()
        val ui = ctl.state.value.uiState!!
        assertTrue(ui.isMyTurn)
        assertTrue(ui.myMana > 0)
        delay(10)
        assertEquals(1, gateway.published.size)
        assertEquals(1, gateway.enqueued.size)
    }

    @Test
    fun hostOnline_accionRemotaIlegal_recibeRejectedYLogueaMotivo() = runBlocking {
        val sink = FakeSink()
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = sink, scope = this)
        ctl.hostMatch(aggroDeck())
        val initial = initialHostState(aggroDeck())
        gateway.hostStateListener?.invoke(initial)

        val snapshot0 = initial.snapshot
        val oppInstance = snapshot0.handOf(opponent).first()
        val bad = MatchAction(
            playerId = opponent,
            actionType = ActionType.PLAY_CARD,
            payload = ActionPayload(instanceId = oppInstance),
            sequence = 0L,
        )
        gateway.hostActionListener?.invoke(bad)

        val log = ctl.state.value.rejectionLog.joinToString { it }
        assertTrue("debería loguear el motivo de rechazo", log.contains("Rechazada"))
        assertEquals(0, gateway.published.size)
    }

    @Test
    fun hostOnline_secuenciaDuplicada_seIgnoraYLoguea() = runBlocking {
        val sink = FakeSink()
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = sink, scope = this)
        ctl.hostMatch(aggroDeck())
        gateway.hostStateListener?.invoke(initialHostState(aggroDeck()))

        ctl.beginTurn()
        gateway.hostActionListener?.invoke(
            MatchAction(me, ActionType.BEGIN_TURN, ActionPayload(), sequence = 0L),
        )
        val log = ctl.state.value.rejectionLog.joinToString { it }
        assertTrue("debería loguear duplicada", log.contains("duplicada"))
    }

    @Test
    fun hostOnline_secuenciaFueraDeOrden_seRechaza() = runBlocking {
        val sink = FakeSink()
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = sink, scope = this)
        ctl.hostMatch(aggroDeck())
        gateway.hostStateListener?.invoke(initialHostState(aggroDeck()))
        ctl.beginTurn() // nextExpected -> 1

        gateway.hostActionListener?.invoke(
            MatchAction(me, ActionType.BEGIN_TURN, ActionPayload(), sequence = 99L),
        )
        val log = ctl.state.value.rejectionLog.joinToString { it }.lowercase()
        assertTrue("debería loguear fuera de orden", log.contains("fuera de orden"))
    }

    @Test
    fun hostOnline_registroDeResultado_seEscribeEnSink() = runBlocking {
        val sink = FakeSink()
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = sink, scope = this)
        ctl.hostMatch(aggroDeck())

        val finalSnap = initialHostState(aggroDeck()).snapshot.copy(
            phase = MatchSnapshot.Phase.FINISHED,
            winner = me,
        )
        val result = MatchResult(MatchId("M-online"), me, WinReason.OPPONENT_DEFEATED, finalSnap)
        gateway.resultListener?.invoke(result)

        delay(10)

        assertEquals(SessionStatus.FINISHED, ctl.state.value.status)
        assertEquals(1, sink.records.size)
        val record = sink.records.single()
        assertTrue(record.victory)
        assertEquals(5, record.xp)
    }

    // ------------------------------------------------------------------
    // Modo CLIENT en línea
    // ------------------------------------------------------------------

    @Test
    fun clientOnline_noRevelaManoDelRival_soloTamano() = runBlocking {
        val sink = FakeSink()
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = sink, scope = this)

        val deck = listOf(CardId("attack-5"))
        ctl.joinMatch(code = "ABC123", deck = deck)
        assertEquals(SessionRole.CLIENT, ctl.state.value.role)
        assertEquals(SessionStatus.ACTIVE, ctl.state.value.status)

        // El objeto myCardOf del controlador usa las instancias del mazo (mismo factory).
        val engineInstance = engine()
        val factory = MatchFactory(DefaultCardCatalog(), engineInstance)
        val myInstances = factory.buildDeck(me, deck).first
        val projection = ClientProjectionWire(
            matchId = MatchId("M-client"),
            phase = MatchSnapshot.Phase.PLAYING,
            turn = 4,
            currentPlayer = me,
            players = listOf(opponent, me),
            heroHealth = mapOf(me to 24, opponent to 6),
            heroMaxHealth = mapOf(me to 30, opponent to 30),
            heroStatuses = mapOf(me to emptyList(), opponent to emptyList()),
            passives = emptyMap(),
            manas = mapOf(me to 6, opponent to 3),
            playedCardLastTurn = emptyMap(),
            winner = null,
            log = listOf("x", "y"),
            clientPlayer = me,
            clientHand = myInstances.toList(),
            numCardsInHostHand = 7,
            version = 1L,
        )
        gateway.projectionListener?.invoke(projection)

        val ui = ctl.state.value.uiState!!
        assertTrue(ui.isMyTurn)
        // El rival solo expone el CONTEO, no la mano.
        assertEquals(7, ui.opponentHandSize)
        assertEquals(1, ui.myHand.size)
        assertEquals(myInstances.first(), ui.myHand[0].instanceId)
        // La vida del avatar de ambos se ve reflejada en la UI del cliente.
        assertEquals(24, ui.myHeroHealth)
        assertEquals(6, ui.opponentHeroHealth)
    }
}
