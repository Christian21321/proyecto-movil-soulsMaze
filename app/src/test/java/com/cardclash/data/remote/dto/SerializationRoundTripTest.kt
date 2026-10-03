package com.cardclash.data.remote.dto

import com.cardclash.domain.model.ActiveStatus
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PassiveBonus
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.StatusDecay
import com.cardclash.domain.model.StatusInstanceId
import com.cardclash.domain.model.StatusType
import com.cardclash.domain.model.UnitStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la capa de serializacion (wire Firestore) de CardClash.
 *
 * Verifican el round-trip consistente `snapshot -> wire -> snapshot` para el
 * documento `stateHost` y la proyeccion filtrada `stateClient`, lo que confirma
 * que la capa compila y no pierde campos relevantes para el motor, incluido el
 * estado del avatar (vida actual/maxima, estados activos y pasivas).
 */
class SerializationRoundTripTest {

    private fun P1() = PlayerId("P1")
    private fun P2() = PlayerId("P2")

    private fun buildSnapshot(): MatchSnapshot {
        val i1 = InstanceId("c1")
        val i2 = InstanceId("c2")
        val i3 = InstanceId("c3")
        val i4 = InstanceId("c4")

        val statusP1 = ActiveStatus(
            instanceId = StatusInstanceId.of(P1(), StatusType.BURN, 1),
            type = StatusType.BURN,
            playerId = P1(),
            durationTurns = 3,
            turnsRemaining = 2,
            decay = StatusDecay.TIMED,
        )
        val statusP2 = ActiveStatus(
            instanceId = StatusInstanceId.of(P2(), StatusType.FROST, 0),
            type = StatusType.FROST,
            playerId = P2(),
            durationTurns = 2,
            turnsRemaining = 2,
            decay = StatusDecay.TIMED,
        )

        return MatchSnapshot(
            matchId = MatchId("M1"),
            phase = MatchSnapshot.Phase.PLAYING,
            turn = 2,
            currentPlayer = P1(),
            players = listOf(P1(), P2()),
            hands = mapOf(P1() to listOf(i1, i2), P2() to listOf(i3)),
            decks = mapOf(P1() to listOf(i3), P2() to listOf(i4)),
            fullDecks = mapOf(P1() to listOf(i3, i4), P2() to listOf(i4)),
            heroHealth = mapOf(P1() to 22, P2() to 7),
            heroMaxHealth = mapOf(P1() to 35, P2() to 30),
            heroStatuses = mapOf(P1() to listOf(statusP1), P2() to listOf(statusP2)),
            passives = mapOf(P1() to listOf(PassiveBonus(UnitStat.MAX_MANA, 2)), P2() to emptyList()),
            manas = mapOf(P1() to 7, P2() to 10),
            baseMaxMana = 10,
            playedCardLastTurn = mapOf(P1() to true, P2() to false),
            cardOf = mapOf(i1 to CardId("minion1"), i2 to CardId("minion1"), i3 to CardId("passiveMana")),
            winner = null,
            log = listOf("turno 1", "robo"),
        )
    }

    @Test
    fun hostSnapshot_roundTrip_preservesAllData() {
        val original = buildSnapshot()

        val envelope = HostSnapshotWire(version = 7L, processedActionSeq = 42L, snapshot = original)
        val wire = envelope.toWire()
        val rebuilt = HostSnapshotWire.fromWire(wire)

        assertEquals(7L, rebuilt.version)
        assertEquals(42L, rebuilt.processedActionSeq)
        val restored = rebuilt.snapshot!!
        assertEquals(original, restored)
    }

    @Test
    fun hostSnapshot_roundTrip_preservaAvatarEstadosYPasivas() {
        val original = buildSnapshot()
        // Comprobación explícita de que el estado del avatar sobrevive al viaje.
        val wire = HostSnapshotWire(1L, 0L, original).toWire()
        val restored = MatchSnapshotDeserializer.fromWire(wire)

        assertEquals(original.heroHealth, restored.heroHealth)
        assertEquals(original.heroMaxHealth, restored.heroMaxHealth)
        assertEquals(original.heroStatuses, restored.heroStatuses)
        assertEquals(original.passives, restored.passives)
        assertEquals(StatusType.BURN, restored.heroStatusesOf(P1()).first().type)
        assertEquals(UnitStat.MAX_MANA, restored.passivesOf(P1()).first().stat)
        assertEquals(2, restored.passivesOf(P1()).first().amount)
    }

    @Test
    fun hostSnapshot_roundTrip_withWinnerAndDefaults() {
        val original = buildSnapshot().let { s ->
            s.copy(
                winner = P2(),
                phase = MatchSnapshot.Phase.FINISHED,
                baseMaxMana = 10, // valor por defecto
                playedCardLastTurn = emptyMap(),
                fullDecks = emptyMap(),
            )
        }

        val wire = HostSnapshotWire(0L, 0L, original).toWire()

        // baseMaxMana ausente debe recaer en el valor por defecto (10)
        val wireWithoutBase = wire - WireFields.BASE_MAX_MANA
        val restored = MatchSnapshotDeserializer.fromWire(wireWithoutBase)

        assertEquals(original, restored)
        assertEquals(10, restored.baseMaxMana)
        assertEquals(P2(), restored.winner)
    }

    @Test
    fun hostSnapshot_envelopeEmptySnapshotDecodesAsNull() {
        val wire = HostSnapshotWire(version = 1L, processedActionSeq = 3L, snapshot = null).toWire()
        val rebuilt = HostSnapshotWire.fromWire(wire)
        assertNull(rebuilt.snapshot)
        assertEquals(1L, rebuilt.version)
        assertEquals(3L, rebuilt.processedActionSeq)
    }

    @Test
    fun clientProjection_roundTrip_filtersHostHand() {
        val snapshot = buildSnapshot()
        val projection = ClientProjectionSerializer.fromSnapshot(snapshot, clientPlayer = P1(), version = 5L)

        // La proyeccion expone solo la mano del cliente y el conteo del host.
        assertEquals(listOf(InstanceId("c1"), InstanceId("c2")), projection.clientHand)
        assertEquals(1, projection.numCardsInHostHand)
        assertEquals(P1(), projection.clientPlayer)
        // El estado del avatar se propaga a la proyección.
        assertEquals(snapshot.heroHealth, projection.heroHealth)
        assertEquals(snapshot.heroMaxHealth, projection.heroMaxHealth)
        assertEquals(snapshot.heroStatuses, projection.heroStatuses)
        assertEquals(snapshot.passives, projection.passives)

        val wire = ClientProjectionSerializer.toWire(projection)
        val restored = ClientProjectionDeserializer.fromWire(wire)

        assertEquals(projection, restored)
        // El wire nunca porta la mano del host, mazos ni cardOf.
        assertTrue(WireFields.CLIENT_HAND in wire)
        assertTrue(WireFields.CLIENT_PLAYER in wire)
        assertTrue(WireFields.NUM_CARDS_IN_HOST_HAND in wire)
        assertTrue(WireFields.HANDS !in wire)
        assertTrue(WireFields.DECKS !in wire)
        assertTrue(WireFields.FULL_DECKS !in wire)
        assertTrue(WireFields.CARD_OF !in wire)
        // Pero sí porta el estado del avatar y las pasivas.
        assertTrue(WireFields.HERO_HEALTH in wire)
        assertTrue(WireFields.HERO_MAX_HEALTH in wire)
        assertTrue(WireFields.HERO_STATUSES in wire)
        assertTrue(WireFields.PASSIVES in wire)
    }
}
