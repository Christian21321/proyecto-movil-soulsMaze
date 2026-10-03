package com.cardclash.domain.model

/**
 * Estado activo (instanciado) aplicado sobre el avatar de un jugador.
 *
 * A diferencia del [StatusType] (que es un "tipo" estatico), [ActiveStatus]
 * representa una ocurrencia concreta: sobre qué avatar, con qué duracion
 * restante y en qué estado de decaimiento.
 *
 * Tras la Fase 1 del rediseno, los estados (BURN/POISON/BLEED/FROST) ya no se
 * aplican sobre unidades de tablero (eliminadas): se aplican sobre el AVATAR de
 * cada jugador, identificado por [playerId].
 *
 * - [turnsRemaining]: cuantos ticks le quedan al estado. Al refrescar el estado
 *   (cuando se reaplica) se vuelve a [durationTurns].
 * - [decay]: [StatusDecay.TIMED] para estados con expiracion; [PERMANENT] para
 *   estados sin fin salvo eliminacion explicita.
 */
data class ActiveStatus(
    val instanceId: StatusInstanceId,
    val type: StatusType,
    val playerId: PlayerId,
    val durationTurns: Int,
    val turnsRemaining: Int,
    val decay: StatusDecay = StatusDecay.TIMED,
) {
    init {
        require(durationTurns > 0) { "La duracion de un estado debe ser positiva" }
        require(turnsRemaining >= 0) { "turnsRemaining no puede ser negativo" }
    }
}
