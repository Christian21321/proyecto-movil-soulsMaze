package com.cardclash.domain.battle

import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardEffect

/**
 * Texto corto del efecto de una carta, con sus dados en notación D&D
 * ("Daño 1d8+1", "Cura 2d4", "BURN 1d4/turno (2 turnos)"). Lo comparten la mano
 * de combate y el constructor de mazos.
 */
object CardText {

    fun describeEffect(card: Card): String {
        if (card.isPassive) {
            val stat = card.passiveStat?.name ?: ""
            return "Pasiva: $stat ${if (card.passiveAmount > 0) "+" else ""}${card.passiveAmount}"
        }
        return when (val effect = card.effect) {
            is CardEffect.Attack -> "Daño ${effect.dice}"
            is CardEffect.Heal -> "Cura ${effect.dice}"
            is CardEffect.Draw -> "Roba ${effect.count}"
            is CardEffect.ApplyStatus -> {
                val status = effect.status
                val tick = status.tickDice?.let { " $it${if (status.escalates) "+t" else ""}/turno" } ?: ""
                val save = status.saveDc?.let { ", salvación CD $it" } ?: ""
                val turns = if (effect.durationTurns == 1) "1 turno" else "${effect.durationTurns} turnos"
                "${status.name}$tick ($turns$save)"
            }
            is CardEffect.PassiveBuff -> "${effect.stat.name} +${effect.amount}"
            is CardEffect.None -> "Sin efecto"
        }
    }
}
