package com.cardclash.domain.model

/**
 * Tipos de estado (aflicciones/buffs) que puede sufrir una unidad en combate.
 *
 * Se separan aqui las propiedades "de datos" (direccion del tick, si respeta o
 * no la defensa, si neutraliza el daño por quemadura) del comportamiento en
 * tiempo real, que queda centralizado en [StatusInteractionRegistry].
 */
enum class StatusType {
    /** Daño constante cada turno (no depende del azar). */
    BURN {
        override val damageKind: DamageKind = DamageKind.FIXED
        override val ignoresDefense: Boolean = false
        override val tickValue: Int = 2
    },

    /** Daño constante cada turno que IGNORA la defensa. */
    POISON {
        override val damageKind: DamageKind = DamageKind.FIXED
        override val ignoresDefense: Boolean = true
        override val tickValue: Int = 3
    },

    /** Daño aleatorio 1d3 cada turno (usa el DiceRoller). */
    BLEED {
        override val damageKind: DamageKind = DamageKind.DICE_1D3
        override val ignoresDefense: Boolean = false
        override val tickValue: Int = 0
    },

    /** Congelacion: salta el turno completo (no roba ni rellena mana). */
    FROST {
        override val damageKind: DamageKind = DamageKind.NONE
        override val ignoresDefense: Boolean = false
        override val tickValue: Int = 0
    };

    /** Forma en que el estado produce daño durante su tick. */
    enum class DamageKind {
        /** Daño fijo cada turno (BURN, POISON). */
        FIXED,

        /** Daño aleatorio 1d3 (BLEED), resuelto con el DiceRoller. */
        DICE_1D3,

        /** Sin daño directo en el tick (FROST). */
        NONE,
    }

    abstract val damageKind: DamageKind
    abstract val ignoresDefense: Boolean
    abstract val tickValue: Int
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
