package com.cardclash.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Turno global en el que la curva de mana de ambos jugadores ya llegó al tope 10. */
private const val LATE_TURN = 21

/**
 * Tests de las invariantes del MODELO de CardClash tras el rediseño de Fase 1
 * (avatar con vida, sin unidades de tablero).
 *
 * Verifican que las clases del dominio se comportan de forma defensiva frente a
 * datos inválidos (`require`/`init`) y que las reglas de cálculo auxiliares
 * (vida del avatar por nivel, mana efectivo, mazo circular, neutralización de
 * estados) son correctas.
 */
class ModelInvariantsTest {

    private fun P1() = PlayerId("P1")
    private fun P2() = PlayerId("P2")

    private fun baseSnapshot(
        current: PlayerId = P1(),
        heroHealth: Map<PlayerId, Int> = mapOf(P1() to 30, P2() to 30),
        heroMaxHealth: Map<PlayerId, Int> = mapOf(P1() to 30, P2() to 30),
        passives: Map<PlayerId, List<PassiveBonus>> = emptyMap(),
    ): MatchSnapshot = MatchSnapshot(
        matchId = MatchId("M"),
        phase = MatchSnapshot.Phase.PLAYING,
        turn = 1,
        currentPlayer = current,
        players = listOf(P1(), P2()),
        hands = emptyMap(),
        decks = emptyMap(),
        fullDecks = emptyMap(),
        heroHealth = heroHealth,
        heroMaxHealth = heroMaxHealth,
        heroStatuses = mapOf(P1() to emptyList(), P2() to emptyList()),
        passives = passives,
        manas = emptyMap(),
    )

    // ---------------------------------------------------------------------
    // Invariantes de Card
    // ---------------------------------------------------------------------

    @Test
    fun card_costNoNegativo() {
        assertThrows(IllegalArgumentException::class.java) {
            Card(CardId("x"), "X", cost = -1, attack = 1, maxHealth = 5)
        }
    }

    @Test
    fun card_attackNoNegativo() {
        assertThrows(IllegalArgumentException::class.java) {
            Card(CardId("x"), "X", cost = 1, attack = -2, maxHealth = 5)
        }
    }

    @Test
    fun card_maxHealthPositivo() {
        assertThrows(IllegalArgumentException::class.java) {
            Card(CardId("x"), "X", cost = 1, attack = 1, maxHealth = 0)
        }
    }

    @Test
    fun card_pasivaRequiereAtributos() {
        assertThrows(IllegalArgumentException::class.java) {
            Card(
                CardId("x"), "X", cost = 1, attack = 0, maxHealth = 2,
                isPassive = true,
            )
        }
    }

    // ---------------------------------------------------------------------
    // Invariantes de ActiveStatus (sobre el avatar, playerId)
    // ---------------------------------------------------------------------

    @Test
    fun activeStatus_duracionPositiva() {
        assertThrows(IllegalArgumentException::class.java) {
            ActiveStatus(
                instanceId = StatusInstanceId.of(P1(), StatusType.BURN, 0),
                type = StatusType.BURN, playerId = P1(),
                durationTurns = 0, turnsRemaining = 0,
            )
        }
    }

    @Test
    fun activeStatus_remainingNoNegativo() {
        assertThrows(IllegalArgumentException::class.java) {
            ActiveStatus(
                instanceId = StatusInstanceId.of(P1(), StatusType.BURN, 0),
                type = StatusType.BURN, playerId = P1(),
                durationTurns = 2, turnsRemaining = -1,
            )
        }
    }

    @Test
    fun activeStatus_datosValidos_aceptados() {
        val st = ActiveStatus(
            instanceId = StatusInstanceId.of(P1(), StatusType.BURN, 0),
            type = StatusType.BURN, playerId = P1(),
            durationTurns = 2, turnsRemaining = 1,
        )
        assertEquals(P1(), st.playerId)
        assertEquals(2, st.durationTurns)
    }

    // ---------------------------------------------------------------------
    // Invariantes de PassiveBonus
    // ---------------------------------------------------------------------

    @Test
    fun passiveBonus_amountNoCero() {
        assertThrows(IllegalArgumentException::class.java) {
            PassiveBonus(UnitStat.MAX_MANA, 0)
        }
    }

    @Test
    fun passiveBuff_amountNoCero() {
        assertThrows(IllegalArgumentException::class.java) {
            CardEffect.PassiveBuff(UnitStat.MAX_MANA, 0)
        }
    }

    // ---------------------------------------------------------------------
    // Invariantes de CardEffect
    // ---------------------------------------------------------------------

    @Test
    fun cardEffect_attackNoNegativo() {
        assertThrows(IllegalArgumentException::class.java) {
            CardEffect.Attack(amount = -3)
        }
    }

    @Test
    fun cardEffect_applyStatusDuracionPositiva() {
        assertThrows(IllegalArgumentException::class.java) {
            CardEffect.ApplyStatus(StatusType.BURN, 0)
        }
    }

    @Test
    fun cardEffect_healNoNegativo() {
        assertThrows(IllegalArgumentException::class.java) {
            CardEffect.Heal(amount = -1)
        }
    }

    // ---------------------------------------------------------------------
    // Neutralización de estados
    // ---------------------------------------------------------------------

    @Test
    fun frostNeutralizaBurn_peroNoAlReves() {
        assertTrue(StatusInteractionRegistry.neutralizes(StatusType.FROST, StatusType.BURN))
        assertFalse(StatusInteractionRegistry.neutralizes(StatusType.BURN, StatusType.FROST))
        assertFalse(StatusInteractionRegistry.neutralizes(StatusType.POISON, StatusType.BURN))
    }

    // ---------------------------------------------------------------------
    // Auxiliares de MatchSnapshot
    // ---------------------------------------------------------------------

    @Test
    fun snapshot_nextPlayer_ciclico() {
        val s = baseSnapshot()
        assertEquals(P2(), s.nextPlayer(P1()))
        assertEquals(P1(), s.nextPlayer(P2()))
    }

    @Test
    fun heroMaxHealthForLevel_nivelInvalido_requiere() {
        assertThrows(IllegalArgumentException::class.java) {
            MatchSnapshot.heroMaxHealthForLevel(0)
        }
    }

    @Test
    fun heroMaxHealthForLevel_formulaAutorizada() {
        assertEquals(30, MatchSnapshot.heroMaxHealthForLevel(1))
        assertEquals(35, MatchSnapshot.heroMaxHealthForLevel(2))
        assertEquals(40, MatchSnapshot.heroMaxHealthForLevel(3))
    }

    @Test
    fun snapshot_effectiveMaxMana_baseMasPasivasDelAvatar() {
        val p = P1()
        val s = baseSnapshot(passives = mapOf(p to listOf(PassiveBonus(UnitStat.MAX_MANA, 3))))
            .copy(turn = LATE_TURN)
        // base 10 + pasiva 3 = 13 (solo importa la pasiva del avatar propio).
        assertEquals(13, s.effectiveMaxMana(p))
        assertEquals(10, s.effectiveMaxMana(P2()))
    }

    @Test
    fun snapshot_fillMana_respetaTopeEfectivo() {
        val p = P1()
        val s = baseSnapshot().copy(manas = mapOf(p to 0, P2() to 0), turn = LATE_TURN)
        val s2 = s.fillMana(p)
        assertEquals(10, s2.manaOf(p))
    }

    @Test
    fun snapshot_fillMana_respetaPasivas() {
        val p = P1()
        val s = baseSnapshot(passives = mapOf(p to listOf(PassiveBonus(UnitStat.MAX_MANA, 2))))
            .copy(manas = mapOf(p to 0, P2() to 0), turn = LATE_TURN)
        val s2 = s.fillMana(p)
        assertEquals(12, s2.manaOf(p))
    }

    @Test
    fun manaCapFor_creceUnoPorTurnoPropioHasta10() {
        val players = listOf(P1(), P2())
        // Turnos globales 1..6 alternan P1, P2, P1, P2...
        val p1 = (1..6).map { MatchSnapshot.manaCapFor(players, P1(), it, 10) }
        val p2 = (1..6).map { MatchSnapshot.manaCapFor(players, P2(), it, 10) }
        assertEquals(listOf(1, 1, 2, 2, 3, 3), p1)
        assertEquals(listOf(0, 1, 1, 2, 2, 3), p2)
        // Tope en baseMaxMana.
        assertEquals(10, MatchSnapshot.manaCapFor(players, P1(), 99, 10))
        // Jugador desconocido: 0.
        assertEquals(0, MatchSnapshot.manaCapFor(players, PlayerId("X"), 5, 10))
    }

    @Test
    fun snapshot_drawCards_circular_sinDuplicados() {
        val p = P1()
        val full = listOf(InstanceId("a"), InstanceId("b"))
        val s = baseSnapshot().copy(
            decks = mapOf(p to full),
            fullDecks = mapOf(p to full),
        )
        // Robar 5 de un mazo de 2: el re-barajado no puede volver a entregar las
        // instancias ya robadas, así que solo salen las 2 existentes.
        val (drawn, rest) = s.drawCards(p, 5) { it.reversed() }
        assertEquals(listOf(InstanceId("a"), InstanceId("b")), drawn)
        assertTrue(rest.isEmpty())
    }

    @Test
    fun snapshot_drawCards_rebarajadoAMitadDeRobo_noRepiteInstancias() {
        val p = P1()
        val full = listOf(InstanceId("a"), InstanceId("b"), InstanceId("c"))
        // Queda solo "a" en la cola: robar 3 obliga a re-barajar a mitad del robo.
        val s = baseSnapshot().copy(
            hands = mapOf(p to emptyList()),
            decks = mapOf(p to listOf(InstanceId("a"))),
            fullDecks = mapOf(p to full),
        )
        // El re-barajado "a la contra" pondría "a" primero si no se excluyera.
        val (drawn, _) = s.drawCards(p, 3) { it.sortedBy { inst -> inst.value } }
        assertEquals(3, drawn.size)
        assertEquals(drawn.size, drawn.toSet().size)
    }

    /** Verificación extra de que un dato sano es aceptado sin error. */
    @Test
    fun invariantes_aceptanDatosValidos() {
        val card = Card(CardId("ok"), "Bien", cost = 2, attack = 3, maxHealth = 5)
        assertEquals(5, card.maxHealth)
        assertEquals(2, card.cost)
        val passive = PassiveBonus(UnitStat.MAX_MANA, 1)
        assertEquals(1, passive.amount)
    }
}
