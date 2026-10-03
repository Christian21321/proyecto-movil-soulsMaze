package com.cardclash.domain.engine

import com.cardclash.domain.model.CardEffect
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.StatusType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Balance del catálogo real ([DefaultCardCatalog]): coste proporcional al
 * efecto y Congelación de 1 turno, que ya no puede bloquear al rival para
 * siempre.
 */
class CatalogBalanceTest {

    private val catalog = DefaultCardCatalog()
    private val p1 = PlayerId("P1")
    private val p2 = PlayerId("P2")

    private fun card(id: String) = catalog.findById(CardId(id))!!

    @Test
    fun ataques_danioProporcionalAlCoste() {
        catalog.all().filter { it.effect is CardEffect.Attack }.forEach { card ->
            val damage = (card.effect as CardEffect.Attack).amount
            assertTrue(
                "${card.id}: daño $damage con coste ${card.cost}",
                damage in (2 * card.cost + 1)..(2 * card.cost + 2),
            )
        }
    }

    @Test
    fun robo_unManaPorCarta() {
        catalog.all().filter { it.effect is CardEffect.Draw }.forEach { card ->
            assertEquals("${card.id}", (card.effect as CardEffect.Draw).count, card.cost)
        }
    }

    @Test
    fun estados_duracion2ExceptoCongelacion1() {
        val durations = catalog.all()
            .mapNotNull { it.effect as? CardEffect.ApplyStatus }
            .associate { it.status to it.durationTurns }

        assertEquals(1, durations[StatusType.FROST])
        assertEquals(2, durations[StatusType.BURN])
        assertEquals(2, durations[StatusType.POISON])
        assertEquals(2, durations[StatusType.BLEED])
    }

    @Test
    fun congelacion_elRivalSaltaUnSoloTurnoYJuegaElSiguiente() {
        val engine = CombatEngine(catalog, SeededDiceRoller(1))
        val frost = InstanceId("frost")
        val rivalDeck = List(8) { InstanceId("r$it") }
        var s = MatchSnapshot(
            matchId = MatchId("M1"),
            phase = MatchSnapshot.Phase.PLAYING,
            turn = 9,
            currentPlayer = p1,
            players = listOf(p1, p2),
            hands = mapOf(p1 to listOf(frost), p2 to emptyList()),
            decks = mapOf(p1 to emptyList(), p2 to rivalDeck),
            fullDecks = mapOf(p1 to listOf(frost), p2 to rivalDeck),
            heroHealth = mapOf(p1 to 30, p2 to 30),
            heroMaxHealth = mapOf(p1 to 30, p2 to 30),
            manas = mapOf(p1 to 10, p2 to 0),
            cardOf = rivalDeck.associateWith { CardId("attack-0") } + (frost to card("status-frost").id),
        )

        s = engine.applyAction(s, CombatEngine.CombatAction.PlayCard(frost, p2)).snapshot
        s = engine.applyAction(s, CombatEngine.CombatAction.EndTurn).snapshot

        // Turno congelado del rival: no roba ni rellena mana.
        s = engine.applyAction(s, CombatEngine.CombatAction.BeginTurn).snapshot
        assertEquals(p2, s.currentPlayer)
        assertEquals(0, s.handOf(p2).size)
        s = engine.applyAction(s, CombatEngine.CombatAction.EndTurn).snapshot
        assertFalse("FROST expira tras 1 turno", s.heroStatusesOf(p2).any { it.type == StatusType.FROST })

        // Turno de P1 y siguiente turno del rival: ya roba, tiene mana y puede jugar.
        s = engine.applyAction(s, CombatEngine.CombatAction.BeginTurn).snapshot
        s = engine.applyAction(s, CombatEngine.CombatAction.EndTurn).snapshot
        s = engine.applyAction(s, CombatEngine.CombatAction.BeginTurn).snapshot
        assertEquals(p2, s.currentPlayer)
        assertEquals(4, s.handOf(p2).size)
        assertTrue(s.manaOf(p2) > 0)
        val played = engine.applyAction(s, CombatEngine.CombatAction.PlayCard(s.handOf(p2).first(), p1)).snapshot
        assertEquals(3, played.handOf(p2).size)
    }
}
