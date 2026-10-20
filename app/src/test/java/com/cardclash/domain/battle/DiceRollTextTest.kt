package com.cardclash.domain.battle

import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.DiceRollEvent
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.RollKind
import com.cardclash.domain.model.RollOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class DiceRollTextTest {

    private val me = PlayerId("ME")
    private val bot = PlayerId("BOT")

    private fun roll(
        roller: PlayerId = me,
        kind: RollKind = RollKind.ATTACK,
        expression: String = "1d8+1",
        d20: Int? = 14,
        faces: List<Int> = listOf(3),
        total: Int = 4,
        outcome: RollOutcome = RollOutcome.HIT,
    ) = DiceRollEvent(1, roller, "Golpe 2", kind, expression, d20, faces, total, outcome)

    @Test
    fun ataqueNormal() {
        val r = roll()
        assertEquals("Tú · d20: 14", DiceRollText.title(r, me))
        assertEquals("Golpe 2 · 1d8+1 → 3+1 = 4 de daño", DiceRollText.detail(r))
    }

    @Test
    fun criticoYPifiaDelRival() {
        val crit = roll(roller = bot, d20 = 20, faces = listOf(3, 7), total = 11, outcome = RollOutcome.CRITICAL)
        assertEquals("Rival · d20: 20 ¡Crítico!", DiceRollText.title(crit, me))
        assertEquals("Golpe 2 · 1d8+1 → 3+7+1 = 11 de daño", DiceRollText.detail(crit))

        val fumble = roll(d20 = 1, faces = emptyList(), total = 0, outcome = RollOutcome.FUMBLE)
        assertEquals("Tú · d20: 1 ¡Pifia!", DiceRollText.title(fumble, me))
        assertEquals("Golpe 2 · 1d8+1 · sin efecto", DiceRollText.detail(fumble))
    }

    @Test
    fun salvacion() {
        val save = roll(kind = RollKind.SAVE, expression = "d20 CD 12", d20 = 15, faces = emptyList(),
            total = 15, outcome = RollOutcome.SAVED)
        assertEquals("Tú · d20: 15 ¡Resiste!", DiceRollText.title(save, me))
        assertEquals("Golpe 2 · d20 CD 12", DiceRollText.detail(save))
    }

    @Test
    fun cardText_muestraLosDadosDeCadaCarta() {
        val catalog = DefaultCardCatalog()
        fun text(id: String) = CardText.describeEffect(catalog.findById(CardId(id))!!)
        assertEquals("Daño 1d8+1", text("attack-2"))
        assertEquals("Cura 2d4", text("heal-1"))
        assertEquals("BURN 1d4/turno (2 turnos)", text("status-burn"))
        assertEquals("BLEED 1d3+t/turno (2 turnos)", text("status-bleed"))
        assertEquals("FROST (1 turno, salvación CD 12)", text("status-frost"))
    }
}
