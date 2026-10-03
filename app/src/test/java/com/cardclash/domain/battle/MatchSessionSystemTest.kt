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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de SISTEMA del [MatchSessionController] (Grupo C, P0), 100% JVM (JUnit 4):
 * cubren contratos transversales de la sesión de combate que no están aislados
 * en una sola clase: turno congelado por FROST, rechazo de jugadas inválidas sin
 * avanzar el estado, no-ops de secuencia, autocorrección de la secuencia en modo
 * HOST tras un fuera-de-orden, idempotencia del registro de resultado y la
 * traducción del resumen de victoria por mazo agotado (con su XP).
 *
 * Reutilizan los mismos FAKES de [MatchSessionControllerTest] (gateway y
 * sumidero), sin Android ni Firestore: el motor real con dice determinista
 * (seed 42) hace el flujo reproducible.
 */
class MatchSessionSystemTest {

    private val me: PlayerId get() = PlayerId("ME")
    private val opponent: PlayerId get() = PlayerId("OPP")

    private fun catalog(): CardCatalog = DefaultCardCatalog()
    private fun engine(): CombatEngine = CombatEngine(DefaultCardCatalog(), SeededDiceRoller(42))

    /** Mazo agresivo (ataque barato y alto) para que la demo finalice rápido. */
    private fun aggroDeck(): List<CardId> = List(12) { CardId("attack-5") }

    /** Mazo barato de ataques para el jugador local. */
    private fun myAttackDeck(): List<CardId> = List(12) { CardId("attack-0") }

    /** Mazo exclusivamente pasivo para el BOT: no puede atacar ni mantener unidades. */
    private fun botPassiveDeck(): List<CardId> = List(12) { CardId("passive-max_mana-+2") }

    /** Mazo inofensivo para el BOT: crea unidades de ataque 0 (nunca mata). */
    private fun harmlessDeck(): List<CardId> = List(12) { CardId("draw-0") }

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

    /** Estado inicial de una partida completa para sembrar al host. */
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
    // Turno congelado (FROST) en el modo local demo
    // ------------------------------------------------------------------

    @Test
    fun localDemo_frostEnAvatarDelRival_leHaceSaltarElTurno() = runBlocking {
        val ctl = MatchSessionController(
            myId = me, engine = engine(), catalog = catalog(),
            gateway = FakeGateway(me, opponent), resultSink = FakeSink(), scope = this,
            maxLogLines = 50,
        )
        // El mazo local incluye una carta FROST que congela el AVATAR del rival;
        // el resto son robos inofensivos para no matar antes a nadie.
        val myDeck = listOf(CardId("status-frost")) + List(11) { CardId("draw-0") }
        ctl.startLocalDemo(myDeck = myDeck, opponentDeck = harmlessDeck())
        // En el modo local el rival del jugador es el BOT controlado por el
        // controlador (PlayerId("BOT")), no el `opponent` de los tests HOST/CLIENT.
        val rival = PlayerId("BOT")

        var hBefore = -1
        var mBefore = -1
        var frosted = false
        for (iter in 0 until 60) {
            val st = ctl.state.value
            if (st.status != SessionStatus.ACTIVE) break
            val ui = st.uiState ?: break
            if (!ui.isMyTurn) { delay(1); continue }
            val frost = ui.myHand.firstOrNull { it.cardId == CardId("status-frost") && it.playable }
            if (!frosted && frost != null) {
                // Congelo el AVATAR del rival; capturo mano/mana previos del BOT.
                hBefore = ui.opponentHandSize
                mBefore = ui.opponentMana
                ctl.playCard(frost.instanceId, rival)
                frosted = true
                ctl.endTurn() // dispara el turno del BOT (comienza congelado) de forma síncrona.
                break
            }
            val playable = ui.myHand.filter { it.playable }
            if (playable.isNotEmpty()) {
                // Robos sin objetivo específico (los ataques apuntan al rival por defecto).
                ctl.playCard(playable.first().instanceId, null)
            } else {
                ctl.endTurn()
            }
        }
        assertTrue("debimos haber aplicado FROST antes de agotar las iteraciones", frosted)

        // El turno congelado del BOT no repone mana ni roba: su mano y su mana
        // quedaron EXACTAMENTE como estaban al final de su turno anterior.
        val uiAfter = ctl.state.value.uiState!!
        assertTrue(uiAfter.isMyTurn)
        assertEquals(hBefore, uiAfter.opponentHandSize)
        assertEquals(mBefore, uiAfter.opponentMana)
    }

    // ------------------------------------------------------------------
    // Rechazo de jugadas inválidas (mana insuficiente / fuera de mano)
    // ------------------------------------------------------------------

    @Test
    fun sesion_rechazaJugada_manaInsuficiente_oFueraDeMano_sinAvanzar() = runBlocking {
        val ctl = MatchSessionController(
            myId = me, engine = engine(), catalog = catalog(),
            gateway = FakeGateway(me, opponent), resultSink = FakeSink(), scope = this,
        )
        ctl.startLocalDemo(myDeck = aggroDeck(), opponentDeck = aggroDeck())
        val turnBefore = ctl.state.value.uiState!!.turn

        // Gasto el mana del turno para forzar cartas no jugables por coste.
        var guard = 0
        while (guard++ < 12) {
            val ui = ctl.state.value.uiState!!
            val playable = ui.myHand.filter { it.playable }
            if (playable.isEmpty()) break
            // Gasto cartas sin objetivo específico (los ataques apuntan al rival).
            ctl.playCard(playable.first().instanceId, null)
        }
        val ui = ctl.state.value.uiState!!
        val unplayable = ui.myHand.firstOrNull { !it.playable && it.cost > ui.myMana }
        assertNotNull("debe quedar una carta en mano no jugable por mana", unplayable)

        // Mana insuficiente (carta REAL de la mano) y carta fuera de la mano.
        ctl.playCard(unplayable!!.instanceId, null)
        ctl.playCard(InstanceId("no-existe"), null)

        assertTrue(ctl.state.value.rejectionLog.isNotEmpty())
        // Nada avanzó: mismo turno, mismo estado de mano y mana.
        val after = ctl.state.value.uiState!!
        assertEquals(turnBefore, after.turn)
        assertEquals(ui.myHand.size, after.myHand.size)
        assertEquals(ui.myMana, after.myMana)
    }

    // ------------------------------------------------------------------
    // Secuencia válida (no-ops de seguridad)
    // ------------------------------------------------------------------

    @Test
    fun secuencia_jugarAntesDeBeginTurn_seRechazaLogueaLRejection() = runBlocking {
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(
            myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = FakeSink(), scope = this,
        )
        ctl.hostMatch(aggroDeck())
        gateway.hostStateListener?.invoke(initialHostState(aggroDeck()))
        val ui0 = ctl.state.value.uiState!!
        // El host arranca SIN mana: aún no se procesó su BEGIN_TURN.
        assertEquals(0, ui0.myMana)
        val turn0 = ui0.turn

        // Jugar antes del BeginTurn (mana 0): el MOTOR rechaza la jugada. El
        // HostSyncEngine lo detecta y lo traduce a HostSyncResult.Rejected: se
        // registra en rejectionLog y NO se publica snapshot ni se avanza estado.
        ctl.playCard(ui0.myHand.first().instanceId, null)
        val after = ctl.state.value.uiState!!

        val log = ctl.state.value.rejectionLog.joinToString { it }
        assertTrue("rejectionLog debe contener el motivo de mana", log.contains("Mana insuficiente"))
        // Sin publicacion: el host no genero writePlan.
        assertEquals(0, gateway.published.size)
        // Estado intacto (mismo turno, mana y mano).
        assertEquals(turn0, after.turn)
        assertEquals(0, after.myMana)
        assertEquals(ui0.myHand.map { it.instanceId }, after.myHand.map { it.instanceId })
    }

    @Test
    fun hostOnline_playCardManaInsuficiente_recibeRejectedSinPublicar() = runBlocking {
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(
            myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = FakeSink(), scope = this,
        )
        ctl.hostMatch(aggroDeck())
        gateway.hostStateListener?.invoke(initialHostState(aggroDeck()))
        // El host arranca SIN mana (turno no iniciado): no hay nada publicado aun.
        assertEquals(0, ctl.state.value.uiState!!.myMana)
        assertEquals(0, gateway.published.size)

        // Jugar una carta de la mano con mana 0: el motor la rechaza por mana
        // insuficiente, el HostSyncEngine la traduce a Rejected y, por tanto,
        // NO publica snapshot.
        ctl.playCard(ctl.state.value.uiState!!.myHand.first().instanceId, null)

        assertTrue(
            "rejectionLog debe contener el motivo de mana",
            ctl.state.value.rejectionLog.joinToString { it }.contains("Mana insuficiente"),
        )
        // El host nunca publico: ni el rechazo ni un estado con version desperdiciada.
        assertEquals(0, gateway.published.size)
        // El estado de juego queda intacto (mismo turno y mana).
        assertEquals(0, ctl.state.value.uiState!!.myMana)
    }

    @Test
    fun secuencia_beginTurnDoble_noEfectivoEnLocal() = runBlocking {
        val ctl = MatchSessionController(
            myId = me, engine = engine(), catalog = catalog(),
            gateway = FakeGateway(me, opponent), resultSink = FakeSink(), scope = this,
        )
        ctl.startLocalDemo(myDeck = myAttackDeck(), opponentDeck = botPassiveDeck())
        val before = ctl.state.value.uiState!!

        // La demo auto-inicia mi turno: un segundo beginTurn es un no-op seguro.
        ctl.beginTurn()

        val after = ctl.state.value.uiState!!
        assertEquals(before.myHand.size, after.myHand.size)
        assertEquals(before.myMana, after.myMana)
        assertEquals(before.turn, after.turn)
        assertTrue(after.isMyTurn)
    }

    // ------------------------------------------------------------------
    // Modo HOST: autocorrección de secuencia tras un fuera-de-orden
    // ------------------------------------------------------------------

    @Test
    fun hostOnline_fueraDeOrden_reenvioSeCorrige_siguienteEsperadoAutoreparado() = runBlocking {
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(
            myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = FakeSink(), scope = this,
        )
        ctl.hostMatch(aggroDeck())
        gateway.hostStateListener?.invoke(initialHostState(aggroDeck()))
        ctl.beginTurn() // seq 0 -> Processed; nextExpectedActionSeq pasa a 1.
        assertTrue(ctl.state.value.uiState!!.myMana > 0)

        // Acción con hueco (seq muy por delante): se rechaza sin avanzar.
        gateway.hostActionListener?.invoke(
            MatchAction(me, ActionType.BEGIN_TURN, ActionPayload(), sequence = 99L),
        )
        val log = ctl.state.value.rejectionLog.joinToString { it }.lowercase()
        assertTrue("debería loguear fuera de orden", log.contains("fuera de orden"))

        // El reenvío lee nextExpectedActionSeq del hostState (sigue en 1): se
        // corrige solo y la acción pendiente sí se procesa.
        ctl.beginTurn()
        assertEquals(2, gateway.enqueued.size)
        assertEquals(1L, gateway.enqueued.last().sequence)
    }

    // ------------------------------------------------------------------
    // Registro de resultado: idempotencia y resumen traducido
    // ------------------------------------------------------------------

    @Test
    fun resultado_idempotente_resultRecordedEvitaDobleEscritura() = runBlocking {
        val sink = FakeSink()
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(
            myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = sink, scope = this,
        )
        ctl.hostMatch(aggroDeck())
        val finalSnap = initialHostState(aggroDeck()).snapshot.copy(
            phase = MatchSnapshot.Phase.FINISHED,
            winner = me,
        )
        val result = MatchResult(MatchId("M-online"), me, WinReason.OPPONENT_DEFEATED, finalSnap)

        // El desenlace llega DOS veces por el canal de resultados (reintento de
        // red, evento duplicado...): solo debe registrarse UNA vez.
        gateway.resultListener?.invoke(result)
        delay(10)
        gateway.resultListener?.invoke(result)
        delay(10)

        assertEquals(SessionStatus.FINISHED, ctl.state.value.status)
        assertEquals(1, sink.records.size)
    }

    @Test
    fun victoriaDeckAgotado_resumenTraducido_yXp5() = runBlocking {
        val sink = FakeSink()
        val gateway = FakeGateway(me, opponent)
        val ctl = MatchSessionController(
            myId = me, engine = engine(), catalog = catalog(),
            gateway = gateway, resultSink = sink, scope = this,
        )
        ctl.hostMatch(aggroDeck())

        // El motor no produce DECK_EXHAUSTED en la demo; llega por el canal de
        // resultados (p. ej. desenlace declarado por el backend).
        val finalSnap = initialHostState(aggroDeck()).snapshot.copy(
            phase = MatchSnapshot.Phase.FINISHED,
            winner = me,
        )
        val result = MatchResult(MatchId("M-online"), me, WinReason.DECK_EXHAUSTED, finalSnap)
        gateway.resultListener?.invoke(result)
        delay(10)

        assertEquals(SessionStatus.FINISHED, ctl.state.value.status)
        val record = sink.records.single()
        assertTrue(record.victory)
        assertEquals(5, record.xp)
        assertEquals("Victoria por DECK_EXHAUSTED", record.summary)
    }
}