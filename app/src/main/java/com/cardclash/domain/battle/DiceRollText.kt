package com.cardclash.domain.battle

import com.cardclash.domain.model.DiceRollEvent
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.RollKind
import com.cardclash.domain.model.RollOutcome

/** Textos de la ventana de tirada de dados de la pantalla de combate. */
object DiceRollText {

    /** Titular: quién tira y el resultado del d20 ("Tú · d20: 20 ¡Crítico!"). */
    fun title(roll: DiceRollEvent, myId: PlayerId): String {
        val who = if (roll.roller == myId) "Tú" else "Rival"
        val d20 = roll.d20?.let { " · d20: $it" } ?: ""
        val verdict = when (roll.outcome) {
            RollOutcome.CRITICAL -> " ¡Crítico!"
            RollOutcome.FUMBLE -> " ¡Pifia!"
            RollOutcome.SAVED -> " ¡Resiste!"
            RollOutcome.NOT_SAVED -> " Falla la salvación"
            RollOutcome.HIT -> ""
        }
        return "$who$d20$verdict"
    }

    /** Detalle: carta, dados y total ("Golpe 2 · 1d8+1 → 3+1 = 4 de daño"). */
    fun detail(roll: DiceRollEvent): String = when (roll.kind) {
        RollKind.SAVE -> "${roll.cardName} · ${roll.expression}"
        RollKind.ATTACK, RollKind.HEAL -> {
            if (roll.outcome == RollOutcome.FUMBLE) {
                "${roll.cardName} · ${roll.expression} · sin efecto"
            } else {
                val bonus = roll.total - roll.faces.sum()
                val bonusText = when {
                    bonus > 0 -> "+$bonus"
                    bonus < 0 -> "$bonus"
                    else -> ""
                }
                val what = if (roll.kind == RollKind.ATTACK) "de daño" else "de curación"
                "${roll.cardName} · ${roll.expression} → ${roll.faces.joinToString("+")}$bonusText = ${roll.total} $what"
            }
        }
    }
}
