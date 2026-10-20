package com.cardclash.domain.model

/**
 * Tipos de estado (aflicciones/buffs) que puede sufrir una unidad en combate.
 *
 * Se separan aqui las propiedades "de datos" (direccion del tick, si respeta o
 * no la defensa, si neutraliza el daño por quemadura) del comportamiento en
 * tiempo real, que queda centralizado en [StatusInteractionRegistry].
 */
enum class StatusType {
    /** Quemadura: 1d4 de daño cada turno. */
    BURN {
        override val tickDice: DiceExpr? = DiceExpr(count = 1, sides = 4)
        override val ignoresDefense: Boolean = false
    },

    /** Veneno: 1d6 de daño cada turno que IGNORA la defensa. */
    POISON {
        override val tickDice: DiceExpr? = DiceExpr(count = 1, sides = 6)
        override val ignoresDefense: Boolean = true
    },

    /** Sangrado: 1d3 + turnos que lleva activo (daño creciente). */
    BLEED {
        override val tickDice: DiceExpr? = DiceExpr(count = 1, sides = 3)
        override val ignoresDefense: Boolean = false
        override val escalates: Boolean = true
    },

    /**
     * Congelacion: salta el turno completo (no roba ni rellena mana). El
     * objetivo tira una salvación d20 contra [saveDc] al recibirla.
     */
    FROST {
        override val tickDice: DiceExpr? = null
        override val ignoresDefense: Boolean = false
        override val saveDc: Int? = 12
    };

    /** Dados de daño de cada tick, o null si el estado no hace daño. */
    abstract val tickDice: DiceExpr?
    abstract val ignoresDefense: Boolean

    /** true si al daño del tick se suman los turnos que lleva activo (BLEED). */
    open val escalates: Boolean = false

    /**
     * Dificultad de la salvación d20 del objetivo: con un resultado >= [saveDc]
     * resiste el estado. null = el estado se aplica siempre.
     */
    open val saveDc: Int? = null
}

/**
 * Direccion de la duracion del estado.
 *
 * Un estado "normal" se refresca con [durationTurns]; el estado aplicado de
 * forma pasiva/permanente no expira mientras la unidad permanezca.
 */
enum class StatusDecay {
    /** El estado expira al agotarse su duracion en turnos. */
    TIMED,

    /** El estado no expira por tiempo (vive mientras vive la unidad). */
    PERMANENT,
}
