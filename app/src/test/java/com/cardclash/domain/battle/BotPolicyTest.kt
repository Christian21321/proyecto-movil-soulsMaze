package com.cardclash.domain.battle

import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.model.ActiveStatus
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PassiveBonus
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.StatusInstanceId
import com.cardclash.domain.model.StatusType
import com.cardclash.domain.model.UnitStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests JVM de la política pura del BOT ([BotPolicy]) con el catálogo real. */
class BotPolicyTest {

    private val catalog = DefaultCardCatalog()
    private val bot = PlayerId("BOT")
    private val me = PlayerId("ME")

    /** Snapshot en el turno del BOT con [hand] (ids del catálogo) y [mana]. */
    private fun snapshot(
        hand: List<String>,
        mana: Int = 10,
        botHealth: Int = 30,
        myHealth: Int = 30,
        botStatuses: List<StatusType> = emptyList(),
        myStatuses: List<StatusType> = emptyList(),
        botPassives: List<PassiveBonus> = emptyList(),
    ): MatchSnapshot {
        val instances = hand.mapIndexed { i, id -> InstanceId("i$i-$id") to CardId(id) }
        fun statuses(player: PlayerId, types: List<StatusType>) = types.mapIndexed { i, t ->
            ActiveStatus(StatusInstanceId.of(player, t, i), t, player, durationTurns = 2, turnsRemaining = 2)
        }
        return MatchSnapshot(
            matchId = MatchId("M1"),
            phase = MatchSnapshot.Phase.PLAYING,
            turn = 2,
            currentPlayer = bot,
            players = listOf(me, bot),
            hands = mapOf(bot to instances.map { it.first }, me to emptyList()),
            decks = emptyMap(),
            heroHealth = mapOf(bot to botHealth, me to myHealth),
            heroMaxHealth = mapOf(bot to 30, me to 30),
            heroStatuses = mapOf(bot to statuses(bot, botStatuses), me to statuses(me, myStatuses)),
            passives = mapOf(bot to botPassives),
            manas = mapOf(bot to mana, me to 0),
            cardOf = instances.toMap(),
        )
    }

    private fun play(s: MatchSnapshot): Pair<String, PlayerId?>? =
        BotPolicy.nextPlay(s, bot, catalog)?.let { s.cardOf.getValue(it.instanceId).value to it.target }

    @Test
    fun congelado_noJuegaNada() {
        assertNull(play(snapshot(listOf("attack-0"), botStatuses = listOf(StatusType.FROST))))
    }

    @Test
    fun sinManaSuficiente_pasa() {
        assertNull(play(snapshot(listOf("attack-5"), mana = 2)))
    }

    @Test
    fun golpeLetal_tienePrioridad() {
        val s = snapshot(listOf("attack-5", "attack-0", "attack-1"), mana = 3, myHealth = 4)
        // attack-1 (4 de daño, coste 1) basta para ganar.
        assertEquals("attack-1" to me, play(s))
    }

    @Test
    fun eligeLaMejorCombinacionDeMana_noLaCartaMasCara() {
        // Con 3 de mana: attack-1 + attack-3 (4 + 6 = 10) vale más que attack-4 (7).
        val s = snapshot(listOf("attack-4", "attack-1", "attack-3"), mana = 3)
        assertEquals("attack-3" to me, play(s))
    }

    @Test
    fun noSeCuraConLaVidaLlena_siCuandoLeFalta() {
        assertNull(play(snapshot(listOf("heal-0"))))
        assertEquals("heal-0" to bot, play(snapshot(listOf("heal-0"), botHealth = 20)))
    }

    @Test
    fun noRepiteUnEstadoQueElRivalYaTiene() {
        assertNull(play(snapshot(listOf("status-burn"), myStatuses = listOf(StatusType.BURN))))
        assertEquals("status-burn" to me, play(snapshot(listOf("status-burn"))))
    }

    @Test
    fun soloRobaConPocasCartas() {
        assertNull(play(snapshot(listOf("draw-0", "heal-0", "heal-1", "heal-2"))))
        assertEquals("draw-0" to null, play(snapshot(listOf("draw-0", "heal-0"))))
    }

    @Test
    fun pasiva_soloSiNoTieneNinguna() {
        assertEquals("passive-max_mana-+2" to null, play(snapshot(listOf("passive-max_mana-+2"))))
        val withPassive = snapshot(
            listOf("passive-max_mana-+2"),
            botPassives = listOf(PassiveBonus(UnitStat.MAX_MANA, 2)),
        )
        assertNull(play(withPassive))
    }
}
