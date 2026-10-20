package com.cardclash.domain.model

/**
 * Expresión de dados al estilo D&D: `NdX+B` (p. ej. `1d8+1`, `2d6`, `1d4`).
 *
 * - [count]: número de dados que se tiran (0 = valor fijo, solo [bonus]).
 * - [sides]: caras de cada dado (d4, d6, d8, d10, d12, d20...).
 * - [bonus]: modificador fijo que se suma a la tirada.
 *
 * Es solo la DEFINICIÓN; la tirada la resuelve el motor con su
 * [com.cardclash.domain.engine.DiceRoller] para que los tests sean
 * reproducibles.
 */
data class DiceExpr(
    val count: Int,
    val sides: Int,
    val bonus: Int = 0,
) {
    init {
        require(count >= 0) { "El número de dados no puede ser negativo" }
        require(sides >= 1) { "Un dado debe tener al menos 1 cara" }
    }

    /** true si la expresión depende del azar (al menos un dado de 2+ caras). */
    val isRandom: Boolean get() = count > 0 && sides > 1

    /** Valor mínimo posible. */
    val min: Int get() = count + bonus

    /** Valor máximo posible. */
    val max: Int get() = count * sides + bonus

    /** Valor medio de la tirada (p. ej. 1d8+1 -> 5.5). */
    val average: Double get() = count * (sides + 1) / 2.0 + bonus

    /** Valor medio redondeado (para el bot y el balance). Exacto si es fijo. */
    val averageRounded: Int get() = Math.round(average).toInt()

    /** Texto en notación D&D: "1d8+1", "2d6", "1d4-1" o "5" si es fijo. */
    override fun toString(): String {
        if (count == 0) return bonus.toString()
        val dice = "${count}d$sides"
        return when {
            bonus > 0 -> "$dice+$bonus"
            bonus < 0 -> "$dice$bonus"
            else -> dice
        }
    }

    companion object {
        /** Expresión sin dados: siempre vale [amount]. */
        fun fixed(amount: Int): DiceExpr = DiceExpr(count = 0, sides = 1, bonus = amount)

        /** Caras del dado de crítico / salvación. */
        const val D20: Int = 20
    }
}

/** Tipo de tirada mostrada al jugador. */
enum class RollKind {
    /** Dados de daño de una carta de ataque. */
    ATTACK,

    /** Dados de curación de una carta de curación. */
    HEAL,

    /** Tirada de salvación d20 del objetivo contra un estado (p. ej. FROST). */
    SAVE,
}

/** Resultado de una tirada con d20. */
enum class RollOutcome {
    /** Tirada normal: se aplica el resultado de los dados. */
    HIT,

    /** 20 natural: se tiran el doble de dados. */
    CRITICAL,

    /** 1 natural: la carta falla y no tiene efecto. */
    FUMBLE,

    /** El objetivo superó la salvación: el estado no se aplica. */
    SAVED,

    /** El objetivo falló la salvación: el estado se aplica. */
    NOT_SAVED,
}

/**
 * Última tirada resuelta por el motor, para que la UI la enseñe.
 *
 * [sequence] crece en cada tirada, así la UI puede animar cada una aunque dos
 * seguidas tengan los mismos valores.
 *
 * @property roller jugador que tira (quien juega la carta, o el objetivo en una salvación).
 * @property expression dados tirados en notación D&D ("1d8+1", "d20 CD 12").
 * @property d20 valor del d20 (crítico o salvación), o null si no hubo.
 * @property faces caras obtenidas en los dados de efecto (vacía si no se tiraron).
 * @property total valor final aplicado (daño, curación o el d20 de la salvación).
 */
data class DiceRollEvent(
    val sequence: Int,
    val roller: PlayerId,
    val cardName: String,
    val kind: RollKind,
    val expression: String,
    val d20: Int?,
    val faces: List<Int>,
    val total: Int,
    val outcome: RollOutcome,
)
