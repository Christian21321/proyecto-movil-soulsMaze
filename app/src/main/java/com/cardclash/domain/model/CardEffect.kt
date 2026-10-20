package com.cardclash.domain.model

/**
 * Efecto de una carta cuando es jugada al tablero.
 *
 * [CardEffect] se expresa como un tipo sellado analizable de forma exhaustiva
 * dentro del motor. Al separar el "efecto" de los "atributos", el motor puede
 * aplicar cada efecto con logica dedicada y mantenible.
 */
sealed class CardEffect {

    /** Efecto vacio: la carta no produce efectos al jugarse. */
    data object None : CardEffect()

    /**
     * Aplica daño directamente a la vida del AVATAR del rival. El daño es la
     * tirada de [dice] (p. ej. 1d8+1); con dados, el motor tira además un d20 de
     * crítico (20 natural = doble de dados, 1 natural = la carta falla).
     * Ya no existen unidades de tablero a las que golpear.
     */
    data class Attack(val dice: DiceExpr) : CardEffect() {
        /** Daño fijo, sin dados ni d20. */
        constructor(amount: Int) : this(DiceExpr.fixed(amount))

        /** Daño medio (exacto si es fijo). */
        val amount: Int get() = dice.averageRounded

        init {
            require(dice.min >= 0) { "El danio de Attack no puede ser negativo" }
        }
    }

    /**
     * Cura la tirada de [dice] al AVATAR objetivo (con tope en su vida máxima).
     * Con dados aplica la misma regla de crítico/pifia con d20 que [Attack].
     */
    data class Heal(val dice: DiceExpr) : CardEffect() {
        /** Curación fija, sin dados ni d20. */
        constructor(amount: Int) : this(DiceExpr.fixed(amount))

        /** Curación media (exacta si es fija). */
        val amount: Int get() = dice.averageRounded

        init {
            require(dice.min >= 0) { "La curacion no puede ser negativa" }
        }
    }

    /** Roba [count] cartas del mazo al final de turno (ademas del robo normal). */
    data class Draw(val count: Int) : CardEffect() {
        init {
            require(count >= 0) { "El robo no puede ser negativo" }
        }
    }

    /** Impone un [status] sobre el AVATAR objetivo con duracion [durationTurns]. */
    data class ApplyStatus(val status: StatusType, val durationTurns: Int) : CardEffect() {
        init {
            require(durationTurns > 0) { "La duracion de un estado debe ser positiva" }
        }
    }

    /**
     * Aplica un buff [stat] permanente por [amount] vinculado al AVATAR que lo
     * juega (modificador de pasiva, p. ej. +MAX_MANA). No es un estado con
     * duracion.
     */
    data class PassiveBuff(val stat: UnitStat, val amount: Int) : CardEffect() {
        init {
            require(amount != 0) { "Un buff pasivo no puede tener cantidad 0" }
        }
    }
}

/**
 * Bonus pasivo permanente aplicado al avatar de un jugador.
 *
 * Antes la pasiva vivia en una unidad del tablero; tras la Fase 1 del rediseno
 * se vincula directamente al avatar (se registra en [com.cardclash.domain.model.
 * MatchSnapshot.passives]). Cada entrada representa un modificador [stat] con su
 * [amount], y [MatchSnapshot.effectiveMaxMana] los suma para el MAX_MANA.
 */
data class PassiveBonus(
    val stat: UnitStat,
    val amount: Int,
) {
    init {
        require(amount != 0) { "Un bonus pasivo no puede tener cantidad 0" }
    }
}

/** Estadistica de unidad susceptible de ser modificada por una pasiva. */
enum class UnitStat {
    ATTACK,
    HEALTH,
    MAX_HEALTH,
    MAX_MANA,
    MANA_REGEN,
}
