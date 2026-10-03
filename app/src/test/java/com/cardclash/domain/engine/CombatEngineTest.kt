package com.cardclash.domain.engine

import com.cardclash.domain.model.ActiveStatus
import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardEffect
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PassiveBonus
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.StatusDecay
import com.cardclash.domain.model.StatusInstanceId
import com.cardclash.domain.model.StatusInteractionRegistry
import com.cardclash.domain.model.StatusType
import com.cardclash.domain.model.UnitStat
import com.cardclash.domain.model.WinReason
import com.cardclash.domain.repository.CardCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del motor de combate con [SeededDiceRoller] (determinista) del rediseño
 * de Fase 1: avatar con vida (sin tableros de unidades).
 *
 * Cubren: vida inicial del avatar por nivel, tope de mano (robo inicial y robo
 * detenido a mano llena), robo por efecto [CardEffect.Draw], mazo circular,
 * FROST salta turno, FROST neutraliza BURN, refresco de estados, ticks de
 * estados sobre el avatar (BURN/POISON/BLEED), victoria por vida a 0, doble KO,
 * validaciones de PlayCard, curación con tope en vida máxima, pasivas MAX_MANA y
 * el hecho de que [CombatEngine.newMatch] NO auto-inicia el turno.
 */
class CombatEngineTest {

    // --- Catálogo de control: cartas con valores exactos conocidos. ---
    private val attacker = Card(
        id = CardId("atk"), name = "Atacar",
        cost = 2, attack = 5, maxHealth = 10,
        effect = CardEffect.Attack(amount = 5),
    )
    private val healer = Card(
        id = CardId("heal"), name = "Curar",
        cost = 2, attack = 1, maxHealth = 6,
        effect = CardEffect.Heal(amount = 4),
    )
    private val burner = Card(
        id = CardId("burn"), name = "Quemar",
        cost = 3, attack = 1, maxHealth = 4,
        effect = CardEffect.ApplyStatus(StatusType.BURN, 2),
    )
    private val poisoner = Card(
        id = CardId("poison"), name = "Envenenar",
        cost = 4, attack = 1, maxHealth = 4,
        effect = CardEffect.ApplyStatus(StatusType.POISON, 2),
    )
    private val bleeder = Card(
        id = CardId("bleed"), name = "Sangrar",
        cost = 3, attack = 1, maxHealth = 4,
        effect = CardEffect.ApplyStatus(StatusType.BLEED, 2),
    )
    private val froster = Card(
        id = CardId("frost"), name = "Congelar",
        cost = 5, attack = 1, maxHealth = 4,
        effect = CardEffect.ApplyStatus(StatusType.FROST, 2),
    )
    private val drawer = Card(
        id = CardId("draw"), name = "Reflexion",
        cost = 2, attack = 0, maxHealth = 2,
        effect = CardEffect.Draw(count = 2),
    )
    private val passivaMana = Card(
        id = CardId("mana"), name = "Aura de mana",
        cost = 4, attack = 0, maxHealth = 2,
        effect = CardEffect.None,
        passiveStat = UnitStat.MAX_MANA, passiveAmount = 2, isPassive = true,
    )

    /** Catálogo con las cartas de control. */
    private fun catalog(vararg extra: Card): CardCatalog = object : CardCatalog {
        private val map: Map<CardId, Card> =
            baseCards().associateBy { it.id } + extra.associateBy { it.id }
        override fun findById(id: CardId): Card? = map[id]
        override fun all(): List<Card> = map.values.toList()
    }

    private fun baseCards(): List<Card> =
        listOf(attacker, healer, burner, poisoner, bleeder, froster, drawer, passivaMana)

    // --- Construcción de objetos base. ---
    private fun P1() = PlayerId("P1")
    private fun P2() = PlayerId("P2")

    private fun inst(id: String) = InstanceId(id)

    /** Estado activo sobre el avatar de [player]. */
    private fun heroStatus(
        player: PlayerId,
        type: StatusType,
        duration: Int = 2,
        remaining: Int = duration,
    ): ActiveStatus = ActiveStatus(
        instanceId = StatusInstanceId.of(player, type, 0),
        type = type,
        playerId = player,
        durationTurns = duration,
        turnsRemaining = remaining,
    )

    /** Construye un snapshot listo para probar (avatar nivel 1 -> 30 de vida). */
    private fun snapshot(
        current: PlayerId,
        manas: Map<PlayerId, Int> = mapOf(P1() to 10, P2() to 10),
        hands: Map<PlayerId, List<InstanceId>> = emptyMap(),
        decks: Map<PlayerId, List<InstanceId>> = emptyMap(),
        heroHealth: Map<PlayerId, Int> = emptyMap(),
        heroStatuses: Map<PlayerId, List<ActiveStatus>> = emptyMap(),
        passives: Map<PlayerId, List<PassiveBonus>> = emptyMap(),
        playedLast: Map<PlayerId, Boolean> = emptyMap(),
        cardOf: Map<InstanceId, CardId> = emptyMap(),
    ): MatchSnapshot {
        val max = 30 // nivel 1
        return MatchSnapshot(
            matchId = MatchId("M1"),
            phase = MatchSnapshot.Phase.PLAYING,
            turn = 1,
            currentPlayer = current,
            players = listOf(P1(), P2()),
            hands = hands,
            decks = decks,
            fullDecks = decks,
            heroHealth = heroHealth.ifEmpty { mapOf(P1() to max, P2() to max) },
            heroMaxHealth = mapOf(P1() to max, P2() to max),
            heroStatuses = heroStatuses,
            passives = passives,
            manas = manas,
            playedCardLastTurn = playedLast,
            cardOf = cardOf,
            log = emptyList(),
        )
    }

    // =====================================================================
    // 1) Vida del avatar según nivel (fórmula 25 + 5 * nivel).
    // =====================================================================
    @Test
    fun vidaAvatar_porNivel_formulaAutorizada() {
        assertEquals(30, MatchSnapshot.heroMaxHealthForLevel(1))
        assertEquals(35, MatchSnapshot.heroMaxHealthForLevel(2))
        assertEquals(40, MatchSnapshot.heroMaxHealthForLevel(3))
        assertEquals(25 + 5 * 7, MatchSnapshot.heroMaxHealthForLevel(7))
    }

    @Test
    fun newMatch_sinNivelPorDefecto_vida30_mana0_mano3_ySinBeginTurnAutomatico() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val o = P2()
        val deck = List(6) { inst("c$it") }
        val s = engine.newMatch(
            matchId = MatchId("M"),
            players = listOf(p, o),
            deckByPlayer = mapOf(p to deck, o to deck),
        )
        // Avatar nivel por defecto 1 -> 30 de vida máxima y actual.
        assertEquals(30, s.heroMaxHealthOf(p))
        assertEquals(30, s.heroHealthOf(p))
        // Mana 0 y mano inicial = 3: newMatch NO auto-inicia el turno.
        assertEquals(0, s.manaOf(p))
        assertEquals(0, s.manaOf(o))
        assertEquals(3, s.handOf(p).size)
        assertEquals(3, s.handOf(o).size)
        assertEquals(MatchSnapshot.Phase.PLAYING, s.phase)
        assertEquals(1, s.turn)
    }

    @Test
    fun newMatch_conNivelPersonalizado_usaVidaSegunNivel() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val s = engine.newMatch(
            matchId = MatchId("M"),
            players = listOf(P1(), P2()),
            deckByPlayer = mapOf(P1() to List(3) { inst("a$it") }, P2() to List(3) { inst("b$it") }),
            heroLevelByPlayer = mapOf(P1() to 2, P2() to 5),
        )
        assertEquals(35, s.heroMaxHealthOf(P1()))
        assertEquals(50, s.heroMaxHealthOf(P2()))
    }

    // =====================================================================
    // 2) Robo con tope de mano: rellena hasta 4; a mano llena no roba.
    // =====================================================================
    @Test
    fun roboInicial_rellenaHasta4EnTotal() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val s0 = snapshot(current = p)
            .copy(decks = mapOf(p to List(10) { inst("c$it") }), fullDecks = mapOf(p to List(10) { inst("c$it") }))
        val s1 = engine.applyAction(s0, CombatEngine.CombatAction.BeginTurn).snapshot
        // Mana rellenado a 10 y mano llena a 4 (0 inicial + 4 robadas).
        assertEquals(10, s1.manaOf(p))
        assertEquals(4, s1.handOf(p).size)
    }

    @Test
    fun roboDetenido_manoLlena_noRobaNada() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val fullHand = List(4) { inst("h$it") }
        val s0 = snapshot(current = p, hands = mapOf(p to fullHand))
            .copy(decks = mapOf(p to List(10) { inst("c$it") }), fullDecks = mapOf(p to List(10) { inst("c$it") }))
        val s1 = engine.applyAction(s0, CombatEngine.CombatAction.BeginTurn).snapshot
        // Mano ya llena a 4: BeginTurn NO roba nada adicional.
        assertEquals(4, s1.handOf(p).size)
        assertTrue(s1.handOf(p).all { it in fullHand })
    }

    // =====================================================================
    // 3) Mazo circular con re-barajado (la extracción no se agota).
    // =====================================================================
    @Test
    fun mazoCircular_robosRepetidosNuncaSeAgotan() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val mazo = listOf(inst("a"), inst("b"))
        var s = snapshot(current = p)
            .copy(decks = mapOf(p to mazo), fullDecks = mapOf(p to mazo))
        var totalRobbado = 0
        repeat(5) {
            s = engine.applyAction(s, CombatEngine.CombatAction.BeginTurn).snapshot
            totalRobbado += s.handOf(p).size
            s = s.copy(hands = s.hands + (p to emptyList<InstanceId>()))
        }
        assertTrue(totalRobbado > 0)
    }

    // =====================================================================
    // 4) FROST salta el turno completo (no roba, no rellena mana).
    // =====================================================================
    @Test
    fun frost_saltaTurnoSinRobarNiRellenarMana() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val s0 = snapshot(
            current = p,
            manas = mapOf(p to 0, P2() to 0),
            heroStatuses = mapOf(p to listOf(heroStatus(p, StatusType.FROST))),
        ).copy(decks = mapOf(p to List(5) { inst("c$it") }), fullDecks = mapOf(p to List(5) { inst("c$it") }))
        val s1 = engine.applyAction(s0, CombatEngine.CombatAction.BeginTurn).snapshot
        // Avatar congelado: no roba ni rellena mana (se queda en 0).
        assertEquals(0, s1.handOf(p).size)
        assertEquals(0, s1.manaOf(p))
    }

    // =====================================================================
    // 5) FROST neutraliza BURN vía StatusInteractionRegistry.
    // =====================================================================
    @Test
    fun frost_neutralizaBurn_enUnRegistro() {
        assertTrue(StatusInteractionRegistry.neutralizes(StatusType.FROST, StatusType.BURN))
        assertFalse(StatusInteractionRegistry.neutralizes(StatusType.BURN, StatusType.FROST))
    }

    @Test
    fun aplicarFrost_eliminaBurnDelAvatar() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val o = P2()
        // El avatar del rival está quemado; se le aplica FROST.
        val s0 = snapshot(
            current = p,
            hands = mapOf(p to listOf(inst("frost"))),
            heroStatuses = mapOf(o to listOf(heroStatus(o, StatusType.BURN))),
            cardOf = mapOf(inst("frost") to CardId("frost")),
        )
        val s1 = engine.applyAction(
            s0,
            CombatEngine.CombatAction.PlayCard(inst("frost"), o),
        ).snapshot
        val statuses = s1.heroStatusesOf(o)
        // BURN eliminado por FROST.
        assertFalse(statuses.any { it.type == StatusType.BURN })
        assertTrue(statuses.any { it.type == StatusType.FROST })
    }

    // =====================================================================
    // 6) Refresco de estados: turnsRemaining = durationTurns.
    // =====================================================================
    @Test
    fun refreshEstados_reaplicaDuracionCompleta() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val casiAgotado = ActiveStatus(
            instanceId = StatusInstanceId.of(p, StatusType.BURN, 0),
            type = StatusType.BURN, playerId = p,
            durationTurns = 2, turnsRemaining = 1,
        )
        val s0 = snapshot(current = p, heroStatuses = mapOf(p to listOf(casiAgotado)))
        val s1 = engine.applyAction(s0, CombatEngine.CombatAction.BeginTurn).snapshot
        val refreshed = s1.heroStatusesOf(p).first { it.type == StatusType.BURN }
        assertEquals(2, refreshed.turnsRemaining)
    }

    // =====================================================================
    // 7) Ticks de estados sobre el avatar: BURN (fijo), POISON (fijo) y BLEED.
    // =====================================================================
    @Test
    fun tickBurn_danioFijoAlAvatar() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(7))
        val p = P1()
        val s0 = snapshot(
            current = p,
            heroHealth = mapOf(p to 10, P2() to 30),
            heroStatuses = mapOf(p to listOf(heroStatus(p, StatusType.BURN))),
        )
        // EndTurn dispara los ticks de estados sobre el avatar.
        val s1 = engine.applyAction(s0, CombatEngine.CombatAction.EndTurn).snapshot
        // BURN hace 2 de daño fijo: 10 - 2 = 8.
        assertEquals(8, s1.heroHealthOf(p))
        // Y consume un turno de duración: de 2 a 1.
        assertEquals(1, s1.heroStatusesOf(p).first().turnsRemaining)
    }

    @Test
    fun tickPoison_danioFijoDe3AlAvatar() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val s0 = snapshot(
            current = p,
            heroHealth = mapOf(p to 10, P2() to 30),
            heroStatuses = mapOf(p to listOf(heroStatus(p, StatusType.POISON))),
        )
        val s1 = engine.applyAction(s0, CombatEngine.CombatAction.EndTurn).snapshot
        // POISON hace 3 fijo: 10 - 3 = 7.
        assertEquals(7, s1.heroHealthOf(p))
    }

    @Test
    fun tickBleed_usa1d3Determinista() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(2))
        val p = P1()
        val s0 = snapshot(
            current = p,
            heroHealth = mapOf(p to 10, P2() to 30),
            heroStatuses = mapOf(p to listOf(heroStatus(p, StatusType.BLEED))),
        )
        val s1 = engine.applyAction(s0, CombatEngine.CombatAction.EndTurn).snapshot
        // BLEED drena entre 1 y 3: 10 - algo en [1;3] -> [7;9].
        assertTrue(s1.heroHealthOf(p) in 7..9)
    }

    // =====================================================================
    // 8) Victoria por vida del avatar a 0 (OPPONENT_DEFEATED), FINISHED.
    // =====================================================================
    @Test
    fun victoria_alBajarRivalAA0_ganaElOtro() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val o = P2()
        // Jugar un ataque de 5 al avatar rival que deja su vida a 0.
        var s = snapshot(
            current = p,
            heroHealth = mapOf(p to 30, o to 5),
            hands = mapOf(p to listOf(inst("atk"))),
            cardOf = mapOf(inst("atk") to CardId("atk")),
        )
        s = engine.applyAction(s, CombatEngine.CombatAction.PlayCard(inst("atk"), o)).snapshot
        assertEquals(0, s.heroHealthOf(o))
        assertEquals(30, s.heroHealthOf(p))
        // Tras el EndTurn se evalúa la victoria.
        val res = engine.applyAction(s, CombatEngine.CombatAction.EndTurn)
        assertNotNull(res.result)
        assertEquals(p, res.result!!.winner)
        assertEquals(WinReason.OPPONENT_DEFEATED, res.result!!.reason)
        assertTrue(res.snapshot.phase == MatchSnapshot.Phase.FINISHED)
    }

    @Test
    fun victoria_dobleKO_prefiereDerrotadoAlRivalDelJugadorActual() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val o = P2()
        // Ambos avatares en 0 al terminar el turno de p (doble KO).
        var s = snapshot(
            current = p,
            heroHealth = mapOf(p to 1, o to 1),
            heroStatuses = mapOf(p to listOf(heroStatus(p, StatusType.BURN, duration = 2))),
            hands = mapOf(p to listOf(inst("atk"))),
            cardOf = mapOf(inst("atk") to CardId("atk")),
        )
        // P1 ataca al rival: o -> 0. Al terminar el turno, el tick BURN de p -> 0.
        s = engine.applyAction(s, CombatEngine.CombatAction.PlayCard(inst("atk"), o)).snapshot
        assertEquals(0, s.heroHealthOf(o))
        val res = engine.applyAction(s, CombatEngine.CombatAction.EndTurn)
        // Precedencia: se declara derrotado al RIVAL del jugador actual (o),
        // por lo que gana el jugador actual (p).
        assertNotNull(res.result)
        assertEquals(p, res.result!!.winner)
    }

    // =====================================================================
    // 9) Validaciones de PlayCard.
    // =====================================================================
    @Test
    fun playCard_cartaFueraDeMano_invalida() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val s0 = snapshot(current = p, hands = mapOf(p to emptyList()))
        val res = engine.applyAction(s0, CombatEngine.CombatAction.PlayCard(inst("atk")))
        // Sin cambios: la carta no está en la mano; el log registra el motivo.
        assertTrue(res.snapshot.handOf(p).isEmpty())
        assertTrue(res.snapshot.manaOf(p) == 10)
    }

    @Test
    fun playCard_noEsTuTurno_invalido() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val s0 = snapshot(
            current = P1(),
            hands = mapOf(P2() to listOf(inst("atk"))),
            cardOf = mapOf(inst("atk") to CardId("atk")),
        )
        val res = engine.applyAction(s0, CombatEngine.CombatAction.PlayCard(inst("atk")))
        // No es turno de P2: no se juega.
        assertEquals(1, res.snapshot.handOf(P2()).size)
        assertEquals(10, res.snapshot.manaOf(P2()))
    }

    @Test
    fun playCard_manaInsuficiente_invalido() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val s0 = snapshot(
            current = p,
            manas = mapOf(p to 1, P2() to 10),
            hands = mapOf(p to listOf(inst("atk"))),
            cardOf = mapOf(inst("atk") to CardId("atk")), // coste 2
        )
        val res = engine.applyAction(s0, CombatEngine.CombatAction.PlayCard(inst("atk")))
        // Mana insuficiente (1 < 2): no se juega, la carta sigue en mano.
        assertEquals(1, res.snapshot.handOf(p).size)
        assertEquals(1, res.snapshot.manaOf(p))
    }

    @Test
    fun playCard_jugadaValida_descuentaManaYQuitaDeLaMano() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val o = P2()
        val before = snapshot(
            current = p,
            heroHealth = mapOf(p to 30, o to 30),
            hands = mapOf(p to listOf(inst("atk"))),
            cardOf = mapOf(inst("atk") to CardId("atk")),
        )
        val s1 = engine.applyAction(before, CombatEngine.CombatAction.PlayCard(inst("atk"), o)).snapshot
        // La carta se descartó de la mano y el mana se descontó (10 - 2 = 8).
        assertTrue(s1.handOf(p).isEmpty())
        assertEquals(8, s1.manaOf(p))
        // Y el ataque golpeó al avatar del rival: 30 - 5 = 25.
        assertEquals(25, s1.heroHealthOf(o))
    }

    // =====================================================================
    // 10) Curación con tope en la vida máxima del avatar.
    // =====================================================================
    @Test
    fun curacion_respetaTopeDeVidaMaxima() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        // Avatar a 28/30: curar 4 no supera los 30.
        val s0 = snapshot(
            current = p,
            heroHealth = mapOf(p to 28, P2() to 30),
            hands = mapOf(p to listOf(inst("heal"))),
            cardOf = mapOf(inst("heal") to CardId("heal")),
        )
        val s1 = engine.applyAction(s0, CombatEngine.CombatAction.PlayCard(inst("heal"), p)).snapshot
        // 28 + 4 = 32, pero el tope es 30.
        assertEquals(30, s1.heroHealthOf(p))
    }

    @Test
    fun curacion_sobreMaximocuraHastaElTope() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        // Avatar ya en su vida máxima: curar no lo supera.
        val s0 = snapshot(
            current = p,
            heroHealth = mapOf(p to 30, P2() to 30),
            hands = mapOf(p to listOf(inst("heal"))),
            cardOf = mapOf(inst("heal") to CardId("heal")),
        )
        val s1 = engine.applyAction(s0, CombatEngine.CombatAction.PlayCard(inst("heal"), p)).snapshot
        assertEquals(30, s1.heroHealthOf(p))
    }

    // =====================================================================
    // 11) Efecto Draw respeta el tope de mano 4.
    // =====================================================================
    @Test
    fun efectoDraw_respetaTopeDeMano4() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        // Mano con 3 cartas + la carta Draw (que roba 2): al jugarla, de las 2
        // cartas robadas solo cabe 1 (para llegar a 4).
        val hand = listOf(inst("draw"), inst("h1"), inst("h2"), inst("h3"))
        val s0 = snapshot(
            current = p,
            hands = mapOf(p to hand),
            cardOf = mapOf(inst("draw") to CardId("draw")),
        ).copy(decks = mapOf(p to List(10) { inst("c$it") }), fullDecks = mapOf(p to List(10) { inst("c$it") }))
        val s1 = engine.applyAction(s0, CombatEngine.CombatAction.PlayCard(inst("draw"))).snapshot
        // Tras robar hasta el tope, la mano queda con 4 cartas (nunca más).
        assertEquals(4, s1.handOf(p).size)
    }

    // =====================================================================
    // 12) Pasivas de mana: elevan el tope efectivo.
    // =====================================================================
    @Test
    fun pasivaDeMana_aumentaTopeEfectivo() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val s0 = snapshot(
            current = p,
            hands = mapOf(p to listOf(inst("mana"))),
            cardOf = mapOf(inst("mana") to CardId("mana")),
        )
        val s1 = engine.applyAction(s0, CombatEngine.CombatAction.PlayCard(inst("mana"))).snapshot
        // La pasiva queda vinculada al avatar: +2 MAX_MANA -> tope 12.
        assertEquals(1, s1.passivesOf(p).size)
        assertEquals(12, s1.effectiveMaxMana(p))
        // Y al iniciar turno se rellena a 12.
        val s2 = engine.applyAction(s1, CombatEngine.CombatAction.BeginTurn).snapshot
        assertEquals(12, s2.manaOf(p))
    }

    // =====================================================================
    // 13) BLEED 1d3 (verificación explícita en rango).
    // =====================================================================
    @Test
    fun bleed_danio1d3_dentroDeRango() {
        val p = P1()
        var minHealth = Int.MAX_VALUE
        var maxDamage = Int.MIN_VALUE
        repeat(20) {
            val engine = CombatEngine(catalog(), SeededDiceRoller(it.toLong()))
            val s0 = snapshot(
                current = p,
                heroHealth = mapOf(p to 10, P2() to 30),
                heroStatuses = mapOf(p to listOf(heroStatus(p, StatusType.BLEED))),
            )
            val s1 = engine.applyAction(s0, CombatEngine.CombatAction.EndTurn).snapshot
            val h = s1.heroHealthOf(p)
            minHealth = minOf(minHealth, h)
            maxDamage = maxOf(maxDamage, 10 - h)
        }
        // El daño de 1d3 está entre 1 y 3.
        assertTrue("daño observado $maxDamage fuera de rango", maxDamage in 1..3)
    }

    // =====================================================================
    // 14) Inmutabilidad: el motor no muta el snapshot de entrada.
    // =====================================================================
    @Test
    fun motorNoMutaSnapshotEntrada() {
        val engine = CombatEngine(catalog(), SeededDiceRoller(1))
        val p = P1()
        val o = P2()
        val s0 = snapshot(
            current = p,
            heroHealth = mapOf(p to 30, o to 30),
            hands = mapOf(p to listOf(inst("atk"))),
            cardOf = mapOf(inst("atk") to CardId("atk")),
        )
        val original = s0
        engine.applyAction(s0, CombatEngine.CombatAction.PlayCard(inst("atk"), o))
        // El snapshot de entrada permanece intacto.
        assertEquals(original, s0)
        assertEquals(1, s0.handOf(p).size)
        assertEquals(10, s0.manaOf(p))
        assertEquals(30, s0.heroHealthOf(o))
    }
}
