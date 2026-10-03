package com.cardclash.data.remote.sync

import com.cardclash.data.remote.dto.ClientProjectionWire
import com.cardclash.domain.engine.CombatEngine
import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.engine.SeededDiceRoller
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.service.MatchFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la orquestacion PURA de sincronizacion del host ([HostSyncEngine]) y
 * del cliente ([ClientSync]). Usan el [CombatEngine] con [SeededDiceRoller]
 * (determinista) y [MatchFactory]/[DefaultCardCatalog] reales: la capa NO toca
 * Firestore, por lo que es 100% testeable en JVM.
 */
class HostSyncEngineTest {

    private fun P1() = PlayerId("P1")
    private fun P2() = PlayerId("P2")

    private fun newEngine(): CombatEngine = CombatEngine(DefaultCardCatalog(), SeededDiceRoller(1))

    /**
     * Crea una partida real con un mazo barato (carta attack-0, coste 1) para
     * ambos jugadores. Tras [MatchFactory.newMatch] P1 tiene 3 cartas en mano y
     * mana 0; su avatar empieza con vida = [MatchSnapshot.heroMaxHealthForLevel] (30).
     */
    private fun initialSnapshot(): MatchSnapshot {
        val engine = newEngine()
        val factory = MatchFactory(DefaultCardCatalog(), engine)
        val deck = List(20) { CardId("attack-0") }
        return factory.newMatch(
            matchId = MatchId("M1"),
            players = listOf(P1(), P2()),
            deckByPlayer = mapOf(P1() to deck, P2() to deck),
        )
    }

    /** Host = P1, cliente = P2. */
    private fun hostEngine(): HostSyncEngine =
        HostSyncEngine(newEngine(), hostPlayerId = P1(), clientPlayerId = P2())

    // ---------------------------------------------------------------------
    // 1) Secuencia correcta: PLAY_CARD valido avanza el snapshot.
    // ---------------------------------------------------------------------
    @Test
    fun playCardValido_conSecuenciaCorrecta_avanzaElSnapshot() {
        val sync = hostEngine()
        var state = sync.initialState(initialSnapshot())

        // BeginTurn (seq 0): mana a 10 y robo de cartas.
        val begin = sync.process(
            MatchAction(P1(), ActionType.BEGIN_TURN, sequence = 0L), state,
        )
        val processed1 = begin as HostSyncEngine.HostSyncResult.Processed
        state = processed1.state
        assertEquals(10, state.snapshot.manaOf(P1()))
        assertTrue(state.snapshot.handOf(P1()).isNotEmpty())

        // PLAY_CARD (seq 1) de la primera carta en mano (attack-0, coste 1).
        val cardToPlay = state.snapshot.handOf(P1()).first()
        val before = state.snapshot
        val play = sync.process(
            MatchAction(P1(), ActionType.PLAY_CARD, ActionPayload(instanceId = cardToPlay), sequence = 1L),
            state,
        )
        val processed2 = play as HostSyncEngine.HostSyncResult.Processed

        // Sin tableros: el ataque daña el avatar rival. La carta sale de la mano
        // y el mana se descuenta (10 - 1).
        assertTrue(processed2.state.snapshot.handOf(P1()).none { it == cardToPlay })
        assertEquals(9, processed2.state.snapshot.manaOf(P1()))
        // El ataque golpea el avatar del rival dejandolo por debajo de su maximo.
        assertEquals(
            MatchSnapshot.heroMaxHealthForLevel(MatchSnapshot.DEFAULT_HERO_LEVEL) - 3,
            processed2.state.snapshot.heroHealthOf(P2()),
        )

        // El estado anterior no se muto.
        assertTrue(before.handOf(P1()).contains(cardToPlay))

        // nextExpectedActionSeq avanzado y version incrementada.
        assertEquals(2L, processed2.state.nextExpectedActionSeq)
        assertEquals(2L, processed2.state.version)
        assertEquals(1L, processed2.writePlan.processedActionSeq)
        assertNull(processed2.matchResult)

        // Idempotencia del payload de PLAY_CARD.
        assertEquals(cardToPlay, processed2.writePlan.hostSnapshotWire.snapshot!!.cardOf.keys.first())
    }

    // ---------------------------------------------------------------------
    // 2) Secuencia repetida (idempotencia) se ignora sin cambiar estado.
    // ---------------------------------------------------------------------
    @Test
    fun secuenciaRepetida_seIgnoraSinCambiarEstado() {
        val sync = hostEngine()
        var state = sync.initialState(initialSnapshot())

        val first = sync.process(
            MatchAction(P1(), ActionType.BEGIN_TURN, sequence = 0L), state,
        ) as HostSyncEngine.HostSyncResult.Processed
        state = first.state

        // Reintento de la misma secuencia 0: Duplicate, estado intacto.
        val duplicate = sync.process(
            MatchAction(P1(), ActionType.BEGIN_TURN, sequence = 0L), state,
        )
        assertTrue(duplicate is HostSyncEngine.HostSyncResult.Duplicate)
        assertEquals(state, duplicate.state)
    }

    // ---------------------------------------------------------------------
    // 3) Secuencia fuera de orden (hueco) se rechaza.
    // ---------------------------------------------------------------------
    @Test
    fun secuenciaFueraDeOrden_seRechaza() {
        val sync = hostEngine()
        val state = sync.initialState(initialSnapshot())

        // Se espera la secuencia 0 pero llega la 5: OutOfOrder.
        val outOfOrder = sync.process(
            MatchAction(P1(), ActionType.BEGIN_TURN, sequence = 5L), state,
        )
        assertTrue(outOfOrder is HostSyncEngine.HostSyncResult.OutOfOrder)
        assertEquals(state, outOfOrder.state)

        // Con estado intacto, la secuencia correcta (0) sigue siendo procesable.
        val valid = sync.process(
            MatchAction(P1(), ActionType.BEGIN_TURN, sequence = 0L), state,
        )
        assertTrue(valid is HostSyncEngine.HostSyncResult.Processed)
    }

    // ---------------------------------------------------------------------
    // 4) Proyeccion cliente: clientHand y numCardsInHostHand correctos.
    // ---------------------------------------------------------------------
    @Test
    fun proyeccionCliente_clientHandYNumCardsCorrectos() {
        val sync = hostEngine()
        var state = sync.initialState(initialSnapshot())

        // Tras BeginTurn de P1, la proyeccion para el cliente (P2):
        // - clientHand = mano de P2 (aun sin jugar, 3 cartas iniciales).
        // - numCardsInHostHand = tamano de la mano de P1 tras robar a 4 (3 + 1).
        val begin = sync.process(
            MatchAction(P1(), ActionType.BEGIN_TURN, sequence = 0L), state,
        ) as HostSyncEngine.HostSyncResult.Processed
        state = begin.state
        val clientWire: ClientProjectionWire = begin.writePlan.clientProjectionWire

        assertEquals(P2(), clientWire.clientPlayer)
        assertEquals(state.snapshot.handOf(P2()), clientWire.clientHand)
        assertEquals(state.snapshot.handOf(P1()).size, clientWire.numCardsInHostHand)
        assertEquals(4, clientWire.numCardsInHostHand)

        // El wire de la proyeccion NO debe incluir la mano del host.
        val wire = com.cardclash.data.remote.dto.ClientProjectionSerializer.toWire(clientWire)
        assertTrue("decks", com.cardclash.data.remote.dto.WireFields.DECKS !in wire)
        assertTrue("cardOf", com.cardclash.data.remote.dto.WireFields.CARD_OF !in wire)
    }

    // ---------------------------------------------------------------------
    // 5) Accion ilegal (jugador sin turno) se rechaza sin cambiar estado.
    // ---------------------------------------------------------------------
    @Test
    fun accionIlegal_jugadorSinTurno_seRechazaSinCambiarEstado() {
        val sync = hostEngine()
        val state = sync.initialState(initialSnapshot())

        // P1 tiene el turno: P2 intenta jugar una carta -> Rejected.
        val cardInP2Hand = state.snapshot.handOf(P2()).first()
        val rejected = sync.process(
            MatchAction(P2(), ActionType.PLAY_CARD, ActionPayload(instanceId = cardInP2Hand), sequence = 0L),
            state,
        )
        assertTrue(rejected is HostSyncEngine.HostSyncResult.Rejected)
        assertEquals(state, rejected.state)
        assertTrue((rejected as HostSyncEngine.HostSyncResult.Rejected).reason.isNotBlank())
    }

    // ---------------------------------------------------------------------
    // 6) Accion PLAY_CARD con carta fuera de mano se rechaza.
    // ---------------------------------------------------------------------
    @Test
    fun playCardCartaFueraDeMano_seRechaza() {
        val sync = hostEngine()
        val state = sync.initialState(initialSnapshot())

        // Instancia que NO esta en la mano de P1.
        val outside = InstanceId("no-tengo-esta-carta")
        val rejected = sync.process(
            MatchAction(P1(), ActionType.PLAY_CARD, ActionPayload(instanceId = outside), sequence = 0L),
            state,
        )
        assertTrue(rejected is HostSyncEngine.HostSyncResult.Rejected)
        assertEquals(state, rejected.state)
    }

    // ---------------------------------------------------------------------
    // 7) PLAY_CARD rechazado por el motor (mana insuficiente / turno no
    //    iniciado) debe devolver Rejected SIN avanzar secuencia ni publicar.
    // ---------------------------------------------------------------------

    /**
     * Partida cuyo turno NO ha iniciado (mana 0) pero con cartas en mano.
     * P1 tiene el turno, fase PLAYING.
     */
    private fun snapshotSinBeginTurn(): MatchSnapshot = initialSnapshot()

    /**
     * Partida con mana reducido (1) y una carta de coste 2 (heal-0) en mano de
     * P1: fuerza un rechazo por "Mana insuficiente" SIN depender del turno no
     * iniciado (mana > 0). Solo [MatchFactory] puebla `cardOf`, que el motor
     * consulta para resolver el coste.
     */
    private fun snapshotManaInsuficienteConCartaEnMano(): MatchSnapshot {
        val eng = newEngine()
        val factory = MatchFactory(DefaultCardCatalog(), eng)
        val deck = List(20) { CardId("heal-0") } // coste 2
        val base = factory.newMatch(
            matchId = MatchId("M1"),
            players = listOf(P1(), P2()),
            deckByPlayer = mapOf(P1() to deck, P2() to deck),
        )
        return base.copy(manas = base.manas + (P1() to 1))
    }

    @Test
    fun playCard_manaInsuficiente_devuelveRejected_sinAvanzarSecuenciaSinPublicar() {
        val sync = hostEngine()
        val state = sync.initialState(snapshotManaInsuficienteConCartaEnMano())
        val cardInHand = state.snapshot.handOf(P1()).first()

        val result = sync.process(
            MatchAction(P1(), ActionType.PLAY_CARD, ActionPayload(instanceId = cardInHand), sequence = 0L),
            state,
        )

        assertTrue(result is HostSyncEngine.HostSyncResult.Rejected)
        val rejected = result as HostSyncEngine.HostSyncResult.Rejected
        // Estado identico: ni seq ni version avanzan, no hay writePlan/publicacion.
        assertEquals(state, rejected.state)
        assertEquals(0L, rejected.state.nextExpectedActionSeq)
        assertEquals(0L, rejected.state.version)
        assertTrue("motivo de mana en el rechazo", rejected.reason.contains("Mana insuficiente"))
    }

    @Test
    fun playCard_antesDeBeginTurn_devuelveRejected() {
        val sync = hostEngine()
        val state = sync.initialState(snapshotSinBeginTurn())
        assertEquals(0, state.snapshot.manaOf(P1()))
        val cardInHand = state.snapshot.handOf(P1()).first()

        val result = sync.process(
            MatchAction(P1(), ActionType.PLAY_CARD, ActionPayload(instanceId = cardInHand), sequence = 0L),
            state,
        )

        assertTrue(result is HostSyncEngine.HostSyncResult.Rejected)
        val rejected = result as HostSyncEngine.HostSyncResult.Rejected
        assertEquals(state, rejected.state)
        assertTrue(rejected.reason.contains("Mana insuficiente"))
        assertEquals(0L, rejected.state.nextExpectedActionSeq)
        assertEquals(0L, rejected.state.version)
    }

    @Test
    fun playCard_corregida_seReintentaExitosamente_enMismoTurno() {
        val sync = hostEngine()
        val state = sync.initialState(snapshotSinBeginTurn())
        val cardInHand = state.snapshot.handOf(P1()).first()

        // Intento inmaduro antes de iniciar turno: Rejected, la secuencia NO se
        // consume (nextExpectedActionSeq sigue en 0). El cliente puede reintentar.
        val rejected = sync.process(
            MatchAction(P1(), ActionType.PLAY_CARD, ActionPayload(instanceId = cardInHand), sequence = 0L),
            state,
        )
        assertTrue(rejected is HostSyncEngine.HostSyncResult.Rejected)
        assertEquals(0L, rejected.state.nextExpectedActionSeq)

        // Reintento limpio en el MISMO turno: al no consumirse la seq, el turno
        // se inicia reusando la MISMA secuencia 0 y la jugada corregida (ya con
        // mana) se procesa como Processed.
        val begin = sync.process(
            MatchAction(P1(), ActionType.BEGIN_TURN, ActionPayload(), sequence = 0L),
            state,
        )
        val processedBegin = begin as HostSyncEngine.HostSyncResult.Processed
        val afterBegin = processedBegin.state
        assertEquals(10, afterBegin.snapshot.manaOf(P1()))
        assertEquals(1L, afterBegin.nextExpectedActionSeq)

        val play = sync.process(
            MatchAction(P1(), ActionType.PLAY_CARD, ActionPayload(instanceId = cardInHand), sequence = 1L),
            afterBegin,
        )
        assertTrue(play is HostSyncEngine.HostSyncResult.Processed)
        val processedPlay = play as HostSyncEngine.HostSyncResult.Processed
        assertEquals(2L, processedPlay.state.nextExpectedActionSeq)
        // La carta jugada sale de la mano del host (sin tableros).
        assertTrue(processedPlay.state.snapshot.handOf(P1()).none { it == cardInHand })
    }

    // ---------------------------------------------------------------------
    // 8) ClientSync: versiones y secuencia monotona.
    // ---------------------------------------------------------------------
    @Test
    fun clientSync_gestionaVersionesYSecuenciaMonotona() {
        val clientSync = ClientSync(initialVersion = 0L, playerId = P2())

        // Proyeccion simulada con version 1: debe aplicarse.
        val v1 = clientProjection(version = 1L, matchId = MatchId("M1"))
        assertTrue(clientSync.shouldApply(v1))
        clientSync.markApplied(v1)

        // Proyeccion con version 0 (mas antigua) no debe aplicarse.
        val stale = clientProjection(version = 0L, matchId = MatchId("M1"))
        assertTrue(!clientSync.shouldApply(stale))

        // Secuencia monotona empezando en 0.
        val a0 = clientSync.nextAction(ActionType.BEGIN_TURN)
        val a1 = clientSync.nextAction(ActionType.END_TURN)
        assertEquals(0L, a0.sequence)
        assertEquals(1L, a1.sequence)
        assertEquals(P2(), a0.playerId)
    }

    private fun clientProjection(
        version: Long,
        matchId: MatchId,
    ): ClientProjectionWire = ClientProjectionWire(
        matchId = matchId,
        phase = MatchSnapshot.Phase.PLAYING,
        turn = 1,
        currentPlayer = P1(),
        players = listOf(P1(), P2()),
        heroHealth = mapOf(P1() to 30, P2() to 30),
        heroMaxHealth = mapOf(P1() to 30, P2() to 30),
        heroStatuses = mapOf(P1() to emptyList(), P2() to emptyList()),
        passives = emptyMap(),
        manas = mapOf(P1() to 10, P2() to 10),
        playedCardLastTurn = emptyMap(),
        winner = null,
        log = emptyList(),
        clientPlayer = P2(),
        clientHand = emptyList(),
        numCardsInHostHand = 0,
        version = version,
    )
}
