package com.cardclash.domain.engine

import com.cardclash.domain.model.ActiveStatus
import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardEffect
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.DiceExpr
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.Rarity
import com.cardclash.domain.model.RollKind
import com.cardclash.domain.model.RollOutcome
import com.cardclash.domain.model.StatusInstanceId
import com.cardclash.domain.model.StatusType
import com.cardclash.domain.repository.CardCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de dados D&D del motor: dados de daño/curación, crítico con 20
 * natural, pifia con 1 natural, salvación d20 contra FROST y ticks de estados.
 */
class DiceRulesTest {

    private val p1 = PlayerId("P1")
    private val p2 = PlayerId("P2")
    private val card = InstanceId("c")

    /** Roller que devuelve los valores de [values] en orden y falla si se agotan. */
    private class ScriptedRoller(vararg values: Int) : DiceRoller {
        private val queue = ArrayDeque(values.toList())
        val remaining: Int get() = queue.size

        override fun nextInt(min: Int, max: Int): Int {
            val value = queue.removeFirstOrNull() ?: error("Tirada inesperada ($min..$max)")
            require(value in min..max) { "$value fuera de $min..$max" }
            return value
        }
    }

    /** Catálogo de una sola carta con id `x`. */
    private fun catalogOf(effect: CardEffect): CardCatalog = object : CardCatalog {
        val only = Card(id = CardId("x"), name = "Carta X", cost = 1, attack = 0, maxHealth = 1, effect = effect)
        override fun findById(id: CardId): Card? = only.takeIf { it.id == id }
        override fun all(): List<Card> = listOf(only)
        override fun rarityOf(id: CardId): Rarity? = Rarity.COMMON
    }

    private fun snapshot(p1Health: Int = 20, p2Statuses: List<ActiveStatus> = emptyList()) = MatchSnapshot(
        matchId = MatchId("M"),
        phase = MatchSnapshot.Phase.PLAYING,
        turn = 1,
        currentPlayer = p1,
        players = listOf(p1, p2),
        hands = mapOf(p1 to listOf(card), p2 to emptyList()),
        decks = mapOf(p1 to emptyList(), p2 to emptyList()),
        heroHealth = mapOf(p1 to p1Health, p2 to 30),
        heroMaxHealth = mapOf(p1 to 30, p2 to 30),
        heroStatuses = mapOf(p1 to emptyList(), p2 to p2Statuses),
        manas = mapOf(p1 to 5, p2 to 0),
        cardOf = mapOf(card to CardId("x")),
    )

    private fun play(effect: CardEffect, roller: DiceRoller, s: MatchSnapshot = snapshot()): MatchSnapshot =
        CombatEngine(catalogOf(effect), roller)
            .applyAction(s, CombatEngine.CombatAction.PlayCard(card))
            .snapshot

    private val d8plus1 = CardEffect.Attack(DiceExpr(1, 8, 1))

    @Test
    fun ataque_tiradaNormal_aplicaDadosMasBonus() {
        val s = play(d8plus1, ScriptedRoller(10, 5))

        assertEquals(30 - 6, s.heroHealthOf(p2))
        val roll = s.lastRoll!!
        assertEquals(RollOutcome.HIT, roll.outcome)
        assertEquals(RollKind.ATTACK, roll.kind)
        assertEquals("1d8+1", roll.expression)
        assertEquals(10, roll.d20)
        assertEquals(listOf(5), roll.faces)
        assertEquals(6, roll.total)
        assertEquals(1, roll.sequence)
    }

    @Test
    fun ataque_20natural_esCriticoYTiraElDobleDeDados() {
        val s = play(d8plus1, ScriptedRoller(20, 3, 7))

        assertEquals(30 - 11, s.heroHealthOf(p2))
        assertEquals(RollOutcome.CRITICAL, s.lastRoll!!.outcome)
        assertEquals(listOf(3, 7), s.lastRoll!!.faces)
    }

    @Test
    fun ataque_1natural_esPifia_noHaceDanioPeroGastaLaCarta() {
        val roller = ScriptedRoller(1)
        val s = play(d8plus1, roller)

        assertEquals(30, s.heroHealthOf(p2))
        assertEquals(RollOutcome.FUMBLE, s.lastRoll!!.outcome)
        assertTrue(s.handOf(p1).isEmpty())
        assertEquals(4, s.manaOf(p1))
        assertEquals("No se tiran dados de daño tras la pifia", 0, roller.remaining)
    }

    @Test
    fun curacion_tiraSusDados_yLaPifiaNoCura() {
        val heal = CardEffect.Heal(DiceExpr(2, 4))

        val healed = play(heal, ScriptedRoller(15, 2, 4), snapshot(p1Health = 10))
        assertEquals(16, healed.heroHealthOf(p1))
        assertEquals(RollKind.HEAL, healed.lastRoll!!.kind)

        val fumbled = play(heal, ScriptedRoller(1), snapshot(p1Health = 10))
        assertEquals(10, fumbled.heroHealthOf(p1))
    }

    @Test
    fun efectoFijo_noTiraD20() {
        val s = play(CardEffect.Attack(amount = 5), ScriptedRoller())

        assertEquals(25, s.heroHealthOf(p2))
        assertNull(s.lastRoll)
    }

    @Test
    fun frost_elRivalLoResisteConSalvacionIgualOMayorQueLaCD() {
        val frost = CardEffect.ApplyStatus(StatusType.FROST, durationTurns = 1)

        val saved = play(frost, ScriptedRoller(12))
        assertFalse(saved.heroStatusesOf(p2).any { it.type == StatusType.FROST })
        assertEquals(RollOutcome.SAVED, saved.lastRoll!!.outcome)
        assertEquals(p2, saved.lastRoll!!.roller)

        val frozen = play(frost, ScriptedRoller(11))
        assertTrue(frozen.heroStatusesOf(p2).any { it.type == StatusType.FROST })
        assertEquals(RollOutcome.NOT_SAVED, frozen.lastRoll!!.outcome)
    }

    @Test
    fun estadosSinSalvacion_seAplicanSinTirar() {
        val s = play(CardEffect.ApplyStatus(StatusType.BURN, durationTurns = 2), ScriptedRoller())

        assertTrue(s.heroStatusesOf(p2).any { it.type == StatusType.BURN })
    }

    @Test
    fun sangrado_sumaLosTurnosQueLlevaActivo() {
        val bleed = ActiveStatus(
            instanceId = StatusInstanceId.of(p1, StatusType.BLEED, 0),
            type = StatusType.BLEED,
            playerId = p1,
            durationTurns = 2,
            turnsRemaining = 1,
        )
        val s = snapshot().copy(heroStatuses = mapOf(p1 to listOf(bleed), p2 to emptyList()))
        val after = CombatEngine(catalogOf(CardEffect.None), ScriptedRoller(2))
            .applyAction(s, CombatEngine.CombatAction.EndTurn)
            .snapshot

        // 1d3 (2) + 1 turno activo = 3.
        assertEquals(17, after.heroHealthOf(p1))
    }

    @Test
    fun diceExpr_notacionYMedia() {
        assertEquals("1d8+1", DiceExpr(1, 8, 1).toString())
        assertEquals("2d6", DiceExpr(2, 6).toString())
        assertEquals("1d4-1", DiceExpr(1, 4, -1).toString())
        assertEquals("5", DiceExpr.fixed(5).toString())
        assertEquals(5.5, DiceExpr(1, 8, 1).average, 0.0)
        assertEquals(2, DiceExpr(1, 8, 1).min)
        assertEquals(9, DiceExpr(1, 8, 1).max)
        assertFalse(DiceExpr.fixed(5).isRandom)
    }
}
