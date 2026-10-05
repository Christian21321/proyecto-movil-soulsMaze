package com.cardclash.domain.battle

import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardEffect
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.StatusType
import com.cardclash.domain.repository.CardCatalog

/**
 * Política PURA del BOT del modo local: decide la siguiente carta a jugar en
 * su turno (o ninguna, para pasar).
 *
 * Reglas:
 * - Congelado: no juega nada (pasa sin generar rechazos).
 * - Si un ataque basta para ganar, lo juega.
 * - Si no, elige la combinación de cartas de la mano que más valor da con el
 *   mana disponible y juega primero la más valiosa de esa combinación.
 * - No se cura si no le falta vida, no repite un estado que el rival ya tiene,
 *   solo roba si tiene pocas cartas y solo juega la pasiva si aún no tiene una.
 *
 * Se vuelve a consultar tras cada jugada, así que un robo puede cambiar el plan.
 */
object BotPolicy {

    /** Una jugada: instancia de la mano y avatar objetivo (null si no aplica). */
    data class Play(val instanceId: InstanceId, val target: PlayerId?)

    /** Mano a partir de la cual robar no aporta valor. */
    private const val DRAW_HAND_THRESHOLD = 2

    fun nextPlay(snapshot: MatchSnapshot, bot: PlayerId, catalog: CardCatalog): Play? {
        if (snapshot.heroStatusesOf(bot).any { it.type == StatusType.FROST }) return null
        val opponent = snapshot.players.firstOrNull { it != bot } ?: return null
        val mana = snapshot.manaOf(bot)

        val options = snapshot.handOf(bot).mapNotNull { inst ->
            val card = snapshot.cardOf[inst]?.let(catalog::findById) ?: return@mapNotNull null
            if (card.cost > mana) return@mapNotNull null
            val value = valueOf(card, snapshot, bot, opponent)
            if (value <= 0) null else Option(inst, card, value, targetOf(card, bot, opponent))
        }
        if (options.isEmpty()) return null

        // Golpe letal: gana ya.
        val opponentHealth = snapshot.heroHealthOf(opponent)
        options
            .filter { (it.card.effect as? CardEffect.Attack)?.amount?.let { dmg -> dmg >= opponentHealth } == true }
            .minByOrNull { it.card.cost }
            ?.let { return Play(it.instanceId, it.target) }

        val best = bestCombination(options, mana)
        val first = best.maxWithOrNull(compareBy<Option>({ it.value }, { it.card.cost })) ?: return null
        return Play(first.instanceId, first.target)
    }

    private data class Option(
        val instanceId: InstanceId,
        val card: Card,
        val value: Int,
        val target: PlayerId?,
    )

    /** Subconjunto de [options] de máximo valor total con coste <= [mana] (mano <= 4, fuerza bruta). */
    private fun bestCombination(options: List<Option>, mana: Int): List<Option> {
        var best = emptyList<Option>()
        var bestValue = 0
        for (mask in 1 until (1 shl options.size)) {
            val subset = options.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
            if (subset.sumOf { it.card.cost } > mana) continue
            val value = subset.sumOf { it.value }
            if (value > bestValue) {
                best = subset
                bestValue = value
            }
        }
        return best
    }

    /** Valor aproximado de jugar [card] ahora, en puntos de vida. 0 = no merece la pena. */
    private fun valueOf(card: Card, snapshot: MatchSnapshot, bot: PlayerId, opponent: PlayerId): Int {
        if (card.isPassive) {
            return if (snapshot.passivesOf(bot).isEmpty()) card.passiveAmount * 2 else 0
        }
        return when (val effect = card.effect) {
            is CardEffect.Attack -> effect.amount
            is CardEffect.Heal -> {
                val missing = snapshot.heroMaxHealthOf(bot) - snapshot.heroHealthOf(bot)
                minOf(effect.amount, missing)
            }
            is CardEffect.Draw ->
                if (snapshot.handOf(bot).size - 1 <= DRAW_HAND_THRESHOLD) effect.count * 2 else 0
            is CardEffect.ApplyStatus -> {
                val alreadyApplied = snapshot.heroStatusesOf(opponent).any { it.type == effect.status }
                if (alreadyApplied) 0 else statusValue(effect.status) * effect.durationTurns
            }
            is CardEffect.PassiveBuff, CardEffect.None -> 0
        }
    }

    /** Valor por turno de cada estado (daño medio; FROST equivale a un turno del rival). */
    private fun statusValue(status: StatusType): Int = when (status) {
        StatusType.BURN -> 2
        StatusType.POISON -> 3
        StatusType.BLEED -> 2
        StatusType.FROST -> 8
    }

    private fun targetOf(card: Card, bot: PlayerId, opponent: PlayerId): PlayerId? = when (card.effect) {
        is CardEffect.Attack, is CardEffect.ApplyStatus -> opponent
        is CardEffect.Heal -> bot
        else -> null
    }
}
