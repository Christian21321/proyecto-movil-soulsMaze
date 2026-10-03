package com.cardclash.domain.model

/**
 * Identificadores fuertemente tipados del dominio de CardClash.
 *
 * Se usa una envoltura [IdValue] generica para evitar confusion entre
 * identificadores de cartas, unidades, estados y partidas. Al ser
 * [data class], la igualdad es estructural, lo que simplifica las
 * comparaciones dentro del motor.
 */
sealed class IdValue(
    open val value: String,
) {
    override fun toString(): String = value

    override fun equals(other: Any?): Boolean =
        other !== null && other::class == this::class && (other as IdValue).value == value

    override fun hashCode(): Int = value.hashCode()
}

/** Identificador de catalogo de una carta (definicion inmutable). */
data class CardId(override val value: String) : IdValue(value)

/** Identificador de instancia de carta en una partida concreta. */
data class InstanceId(override val value: String) : IdValue(value)

/**
 * Identificador de estado activo aplicado al avatar de un jugador.
 *
 * Los estados ya no se aplican sobre "unidades en tablero" (eliminado en la
 * Fase 1 del rediseno): ahora viven sobre el AVATAR de cada jugador. Por eso el
 * identificador se genera a partir del [PlayerId] dueno del avatar, y no de una
 * unidad.
 */
data class StatusInstanceId(override val value: String) : IdValue(value) {
    companion object {
        fun of(playerId: PlayerId, status: StatusType, ordinal: Int): StatusInstanceId =
            StatusInstanceId("${playerId.value}-${status}-$ordinal")
    }
}

/** Identificador de jugador (cadenas de caracteres opacas, p.ej. "P1", "P2"). */
data class PlayerId(override val value: String) : IdValue(value)

/** Identificador de partida. */
data class MatchId(override val value: String) : IdValue(value)
