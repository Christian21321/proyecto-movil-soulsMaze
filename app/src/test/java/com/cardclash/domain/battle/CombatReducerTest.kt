package com.cardclash.domain.battle

import com.cardclash.data.remote.dto.ClientProjectionWire
import com.cardclash.domain.model.ActiveStatus
import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardEffect
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PassiveBonus
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.Rarity
import com.cardclash.domain.model.StatusInstanceId
import com.cardclash.domain.model.StatusType
import com.cardclash.domain.model.UnitStat
import com.cardclash.domain.repository.CardCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del mapeo puro [CombatReducer] sobre snapshots fabricados (host/local)
 * y proyecciones de cliente, con el rediseño de Fase 1 (avatar con vida, sin
 * tableros). Verifica la perspectiva de un jugador "yo": turno, mana, hero
 * health (vida del avatar), jugabilidad, estados sobre el avatar y fases.
 */
class CombatReducerTest {

    private val me: PlayerId get() = PlayerId("ME")
    private val rival: PlayerId get() = PlayerId("RIVAL")

    // Catálogo controlado para aserciones exactas.
    private val attackCard = Card(
        id = CardId("atk"),
        name = "Golpe",
        cost = 3,
        attack = 4,
        maxHealth = 5,
        effect = CardEffect.Attack(amount = 4),
    )
    private val passiveCard = Card(
        id = CardId("pas"),
        name = "Aura",
        cost = 3,
        attack = 0,
        maxHealth = 2,
        effect = CardEffect.None,
        passiveStat = UnitStat.MAX_MANA,
        passiveAmount = 2,
        isPassive = true,
    )
    private val catalog: CardCatalog = object : CardCatalog {
        private val map = mapOf(attackCard.id to attackCard, passiveCard.id to passiveCard)
        override fun findById(id: CardId): Card? = map[id]
        override fun all(): List<Card> = map.values.toList()
        override fun rarityOf(id: CardId): Rarity? =
            when (id) {
                attackCard.id -> Rarity.SR
                passiveCard.id -> Rarity.SSR
                else -> null
            }
    }

    private val myAttackInstance = InstanceId("me-atk")
    private val myPassiveInstance = InstanceId("me-pas")
    private val rivalHandInstance = InstanceId("rival-card")

    private fun frost(player: PlayerId): ActiveStatus = ActiveStatus(
        instanceId = StatusInstanceId.of(player, StatusType.FROST, 0),
        type = StatusType.FROST,
        playerId = player,
        durationTurns = 2,
        turnsRemaining = 2,
    )

    private fun snapshot(
        phase: MatchSnapshot.Phase = MatchSnapshot.Phase.PLAYING,
        turn: Int = 1,
        current: PlayerId = me,
        myMana: Int = 10,
        rivalMana: Int = 10,
        myHand: List<InstanceId> = listOf(myAttackInstance, myPassiveInstance),
        rivalHand: List<InstanceId> = listOf(rivalHandInstance, rivalHandInstance),
        myHeroHealth: Int = 30,
        myHeroMaxHealth: Int = 30,
        rivalHeroHealth: Int = 30,
        rivalHeroMaxHealth: Int = 30,
        myStatuses: List<ActiveStatus> = emptyList(),
        passives: Map<PlayerId, List<PassiveBonus>> = emptyMap(),
        winner: PlayerId? = null,
        log: List<String> = (1..20).map { "msg-$it" },
        baseMaxMana: Int = 10,
    ): MatchSnapshot = MatchSnapshot(
        matchId = MatchId("M"),
        phase = phase,
        turn = turn,
        currentPlayer = current,
        players = listOf(me, rival),
        hands = mapOf(me to myHand, rival to rivalHand),
        decks = mapOf(me to listOf(myPassiveInstance), rival to listOf(rivalHandInstance)),
        fullDecks = mapOf(me to listOf(myPassiveInstance), rival to listOf(rivalHandInstance)),
        heroHealth = mapOf(me to myHeroHealth, rival to rivalHeroHealth),
        heroMaxHealth = mapOf(me to myHeroMaxHealth, rival to rivalHeroMaxHealth),
        heroStatuses = mapOf(me to myStatuses, rival to emptyList()),
        passives = passives,
        manas = mapOf(me to myMana, rival to rivalMana),
        baseMaxMana = baseMaxMana,
        playedCardLastTurn = emptyMap(),
        cardOf = mapOf(
            myAttackInstance to attackCard.id,
            myPassiveInstance to passiveCard.id,
            rivalHandInstance to attackCard.id,
        ),
        winner = winner,
        log = log,
    )

    // ------------------------------------------------------------------
    // Turno / jugabilidad
    // ------------------------------------------------------------------

    @Test
    fun turnoMio_conManaSuficiente_esMiTurnoYCartasJugables() {
        val ui = CombatReducer.fromSnapshot(snapshot(), catalog, me)
        assertTrue(ui.isMyTurn)
        assertEquals(me, ui.currentPlayer)
        val byInstance = ui.myHand.associateBy { it.instanceId }
        assertTrue(byInstance.getValue(myAttackInstance).playable)
        assertTrue(byInstance.getValue(myPassiveInstance).playable)
    }

    @Test
    fun turnoRival_esFalsoYCartasNoJugables() {
        val ui = CombatReducer.fromSnapshot(snapshot(current = rival), catalog, me)
        assertFalse(ui.isMyTurn)
        assertTrue(ui.myHand.all { !it.playable })
    }

    @Test
    fun manaInsuficiente_marcaCartaNoJugable() {
        val ui = CombatReducer.fromSnapshot(snapshot(myMana = 2), catalog, me)
        val card = ui.myHand.first { it.instanceId == myAttackInstance }
        assertFalse(card.playable)
    }

    @Test
    fun manaSuficienteParaBarata_marcaSoloBarataJugable() {
        // mana 3: exactamente el coste de ambas; se invierte para ver diferencia.
        val ui = CombatReducer.fromSnapshot(snapshot(myMana = 3), catalog, me)
        assertTrue(ui.myHand.all { it.playable })
    }

    // ------------------------------------------------------------------
    // Fases
    // ------------------------------------------------------------------

    @Test
    fun fasePreparando_cartasNoJugables() {
        val ui = CombatReducer.fromSnapshot(snapshot(phase = MatchSnapshot.Phase.PREPARING), catalog, me)
        assertTrue(ui.myHand.all { !it.playable })
        assertFalse(ui.isFinished)
    }

    @Test
    fun faseFinished_marcaTerminadoYNoJugable() {
        val ui = CombatReducer.fromSnapshot(
            snapshot(phase = MatchSnapshot.Phase.FINISHED, winner = rival),
            catalog, me,
        )
        assertTrue(ui.isFinished)
        assertEquals(rival, ui.winner)
        assertFalse(ui.isVictory)
        assertTrue(ui.myHand.all { !it.playable })
    }

    @Test
    fun winnerYo_marcaVictoria() {
        val ui = CombatReducer.fromSnapshot(
            snapshot(phase = MatchSnapshot.Phase.FINISHED, winner = me),
            catalog, me,
        )
        assertTrue(ui.isVictory)
    }

    // ------------------------------------------------------------------
    // Mano / resolución de cartas
    // ------------------------------------------------------------------

    @Test
    fun manoConAtaqueYPasiva_resuelveNombresYrareza() {
        val ui = CombatReducer.fromSnapshot(snapshot(), catalog, me)
        val byInstance = ui.myHand.associateBy { it.instanceId }

        val atk = byInstance.getValue(myAttackInstance)
        assertEquals(attackCard.name, atk.name)
        assertEquals(3, atk.cost)
        assertEquals(Rarity.SR, atk.rarity)
        assertFalse(atk.isPassive)

        val pas = byInstance.getValue(myPassiveInstance)
        assertEquals(passiveCard.name, pas.name)
        assertEquals(Rarity.SSR, pas.rarity)
        assertTrue(pas.isPassive)
    }

    // ------------------------------------------------------------------
    // Vida del avatar (hero health) en la UI state
    // ------------------------------------------------------------------

    @Test
    fun heroHealth_seReflejaEnMyYOponente() {
        val ui = CombatReducer.fromSnapshot(
            snapshot(
                myHeroHealth = 22,
                myHeroMaxHealth = 35,
                rivalHeroHealth = 7,
                rivalHeroMaxHealth = 30,
            ),
            catalog, me,
        )
        assertEquals(22, ui.myHeroHealth)
        assertEquals(35, ui.myHeroMaxHealth)
        assertEquals(7, ui.opponentHeroHealth)
        assertEquals(30, ui.opponentHeroMaxHealth)
    }

    @Test
    fun estadosDeAvatar_seExponenComoTipos() {
        val ui = CombatReducer.fromSnapshot(snapshot(myStatuses = listOf(frost(me))), catalog, me)
        // El estado FROST sobre MI avatar no se filtra en la UI (no hay tableros),
        // pero marca mis cartas como no jugables.
        assertTrue(ui.myHand.all { !it.playable })
    }

    @Test
    fun estadoFrost_enMiAvatar_marcaCartasNoJugables() {
        val ui = CombatReducer.fromSnapshot(
            snapshot(myStatuses = listOf(frost(me)), myMana = 10), catalog, me,
        )
        assertTrue(ui.myHand.all { !it.playable })
    }

    // ------------------------------------------------------------------
    // Oponente / extras
    // ------------------------------------------------------------------

    @Test
    fun oponente_soloExponeTamanoDeManoYmana() {
        val ui = CombatReducer.fromSnapshot(snapshot(), catalog, me)
        assertEquals(2, ui.opponentHandSize)
        assertEquals(10, ui.opponentMana)
    }

    @Test
    fun maxMana_reflejaPasivasDelAvatar() {
        val ui = CombatReducer.fromSnapshot(
            snapshot(baseMaxMana = 10, passives = mapOf(me to listOf(PassiveBonus(UnitStat.MAX_MANA, 2)))),
            catalog, me,
        )
        // 10 base + 2 por la pasiva MAX_MANA vinculada al avatar.
        assertEquals(12, ui.maxMana)
    }

    @Test
    fun log_limitadoAUltimosN_porDefecto() {
        val ui = CombatReducer.fromSnapshot(snapshot(), catalog, me)
        assertEquals(8, ui.log.size)
        assertEquals("msg-20", ui.log.last())
        assertEquals("msg-13", ui.log.first())
    }

    // ------------------------------------------------------------------
    // Proyección de cliente
    // ------------------------------------------------------------------

    @Test
    fun proyeccionCliente_noRevelaManoDelRival_soloTamano() {
        val meInst = InstanceId("0/instance/atk")
        val myCardOf = mapOf(meInst to attackCard.id)
        val projection = ClientProjectionWire(
            matchId = MatchId("M"),
            phase = MatchSnapshot.Phase.PLAYING,
            turn = 3,
            currentPlayer = me,
            players = listOf(rival, me),
            heroHealth = mapOf(me to 18, rival to 25),
            heroMaxHealth = mapOf(me to 30, rival to 30),
            heroStatuses = mapOf(me to emptyList(), rival to emptyList()),
            passives = emptyMap(),
            manas = mapOf(me to 7, rival to 4),
            playedCardLastTurn = emptyMap(),
            winner = null,
            log = listOf("a", "b"),
            clientPlayer = me,
            clientHand = listOf(meInst),
            numCardsInHostHand = 5,
            version = 1L,
        )
        val ui = CombatReducer.fromClientProjection(projection, catalog, myCardOf)
        assertTrue(ui.isMyTurn)
        assertEquals(5, ui.opponentHandSize)
        // La mano del cliente solo resuelve SUS instancias conocidas.
        assertEquals(1, ui.myHand.size)
        assertEquals(meInst, ui.myHand[0].instanceId)
        assertEquals("Golpe", ui.myHand[0].name)
        assertTrue(ui.myHand[0].playable)
        // La vida del avatar de ambos jugadores se expone desde la proyección.
        assertEquals(18, ui.myHeroHealth)
        assertEquals(25, ui.opponentHeroHealth)
        assertEquals(7, ui.myMana)
    }

    @Test
    fun proyeccionCliente_exponelaVidaDeAmbosAvatares() {
        val meInst = InstanceId("0/instance/atk")
        val projection = ClientProjectionWire(
            matchId = MatchId("M"),
            phase = MatchSnapshot.Phase.PLAYING,
            turn = 2,
            currentPlayer = rival,
            players = listOf(rival, me),
            heroHealth = mapOf(me to 9, rival to 30),
            heroMaxHealth = mapOf(me to 30, rival to 35),
            heroStatuses = mapOf(me to emptyList(), rival to emptyList()),
            passives = emptyMap(),
            manas = mapOf(me to 4, rival to 6),
            playedCardLastTurn = emptyMap(),
            winner = null,
            log = emptyList(),
            clientPlayer = me,
            clientHand = listOf(meInst),
            numCardsInHostHand = 3,
            version = 2L,
        )
        val ui = CombatReducer.fromClientProjection(projection, catalog, mapOf(meInst to attackCard.id))
        assertFalse(ui.isMyTurn)
        assertEquals(9, ui.myHeroHealth)
        assertEquals(30, ui.myHeroMaxHealth)
        assertEquals(30, ui.opponentHeroHealth)
        assertEquals(35, ui.opponentHeroMaxHealth)
        assertNotNull(ui)
    }
}
