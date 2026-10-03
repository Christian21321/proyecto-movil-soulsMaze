package com.cardclash.data.remote.sync

import com.cardclash.domain.engine.CombatEngine
import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.engine.SeededDiceRoller
import com.cardclash.domain.model.CardId
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
 * Tests JVM (JUnit 4) del constructor PURO [HostWritePlanBuilder]: derivación
 * del plan de escritura que el host publica (stateHost + stateClient + metadata)
 * desde un [HostSyncState] ya procesado, sin tocar Firestore.
 */
class HostWritePlanBuilderTest {

    private val me = PlayerId("ME")
    private val opp = PlayerId("OPP")
    private val deck = listOf(CardId("attack-0"), CardId("attack-1"), CardId("heal-0"))

    private fun engine(): CombatEngine = CombatEngine(DefaultCardCatalog(), SeededDiceRoller(42))

    /** Estado inicial de una partida asentada con ambos jugadores (mano 4). */
    private fun initialState(): HostSyncState {
        val eng = engine()
        val snapshot = MatchFactory(DefaultCardCatalog(), eng).newMatch(
            matchId = MatchId("M-online"),
            players = listOf(me, opp),
            deckByPlayer = mapOf(me to deck, opp to deck),
        )
        return HostSyncEngine(eng, hostPlayerId = me, clientPlayerId = opp).initialState(snapshot)
    }

    @Test
    fun build_conPartidaAsentada_derivaPlanCompleto() {
        val state = initialState()
        val plan = HostWritePlanBuilder.build(state, me)

        assertNotNull("debería derivar el plan con ambos jugadores", plan)
        plan!!

        // stateHost: mismo snapshot, version y secuencia esperada.
        assertEquals(state.version, plan.hostSnapshotWire.version)
        assertEquals(state.nextExpectedActionSeq, plan.hostSnapshotWire.processedActionSeq)
        assertEquals(state.snapshot, plan.hostSnapshotWire.snapshot)

        // metadata: identidad y estado activo derivado del snapshot.
        assertEquals(state.snapshot.matchId, plan.metadata.matchId)
        assertEquals(state.snapshot.phase, plan.metadata.phase)
        assertEquals(MatchStatus.ACTIVE, plan.metadata.status)

        // El plan marca la secuencia concreta que se difunde como procesada.
        assertEquals(state.nextExpectedActionSeq, plan.processedActionSeq)
    }

    @Test
    fun build_sinRival_devuelveNull() {
        val state = initialState()
        val solo = state.copy(snapshot = state.snapshot.copy(players = listOf(me)))
        assertNull("sin rival no hay proyección cliente que publicar", HostWritePlanBuilder.build(solo, me))
    }

    @Test
    fun build_proyeccionCliente_noRevelaLaManoDelHost() {
        // Mazos DISTINTOS por jugador: las instancias se derivan del mazo, por lo
        // que con mazos distintos los IDs no colisionan y el filtrado es verificable.
        val myDeck = listOf(CardId("attack-0"), CardId("attack-1"), CardId("heal-0"))
        val oppDeck = listOf(CardId("draw-0"), CardId("status-frost"))
        val eng = engine()
        val snapshot = MatchFactory(DefaultCardCatalog(), eng).newMatch(
            matchId = MatchId("M-online"),
            players = listOf(me, opp),
            deckByPlayer = mapOf(me to myDeck, opp to oppDeck),
        )
        val state = HostSyncEngine(eng, hostPlayerId = me, clientPlayerId = opp).initialState(snapshot)
        val myInstances = MatchFactory(DefaultCardCatalog(), engine()).buildDeck(me, myDeck).first

        val plan = HostWritePlanBuilder.build(state, me)!!
        val projection = plan.clientProjectionWire

        // El cliente ve su propia mano (instancias OPP) y solo el CONTEO del host.
        assertEquals(opp, projection.clientPlayer)
        assertEquals(state.snapshot.handOf(opp), projection.clientHand)
        assertEquals(state.snapshot.handOf(me).size, projection.numCardsInHostHand)

        // Ninguna instancia del host se filtra en la mano del cliente.
        assertTrue("la mano del cliente no debe contener instancias del host",
            projection.clientHand.none { it in myInstances })
    }

    @Test
    fun build_reflejaVersionYSecuenciaTrasProcesarUnaAccion() {
        val eng = engine()
        // Partida asentada donde el primer turno es del host.
        val snapshot = MatchFactory(DefaultCardCatalog(), eng).newMatch(
            matchId = MatchId("M-online"),
            players = listOf(me, opp),
            deckByPlayer = mapOf(me to deck, opp to deck),
        )
        val sync = HostSyncEngine(eng, hostPlayerId = me, clientPlayerId = opp)
        var state = sync.initialState(snapshot)

        // Comienza el turno del host: proceso aceptado -> version 1, seq esperada 1.
        val result = sync.process(
            MatchAction(playerId = me, actionType = ActionType.BEGIN_TURN, payload = ActionPayload(), sequence = 0L),
            state,
        )
        assertTrue(result is HostSyncEngine.HostSyncResult.Processed)
        state = (result as HostSyncEngine.HostSyncResult.Processed).state

        val plan = HostWritePlanBuilder.build(state, me)!!
        assertEquals(1L, plan.hostSnapshotWire.version)
        assertEquals(1L, plan.processedActionSeq)
        assertEquals(1L, plan.hostSnapshotWire.processedActionSeq)
    }

    @Test
    fun build_dePartidaFinalizada_derivaStatusFinished() {
        val state = initialState().copy(
            snapshot = initialState().snapshot.copy(
                phase = MatchSnapshot.Phase.FINISHED,
                winner = me,
            ),
        )
        val plan = HostWritePlanBuilder.build(state, me)!!
        assertEquals(MatchStatus.FINISHED, plan.metadata.status)
        assertEquals(me, plan.metadata.winner)
    }
}