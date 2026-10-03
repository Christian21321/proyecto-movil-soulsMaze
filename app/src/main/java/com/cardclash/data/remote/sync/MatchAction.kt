package com.cardclash.data.remote.sync

import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.PlayerId

/**
 * Tipo de accion recibida del cliente, alineado 1:1 con la whitelist de
 * `actions/{actionId}` de ADR-009 y con [com.cardclash.domain.engine.CombatEngine.CombatAction].
 */
enum class ActionType {
    BEGIN_TURN,

    /** Juega [payload.instanceId], con objetivo opcional [payload.targetPlayer]. */
    PLAY_CARD,

    END_TURN,
}

/**
 * Carga (payload) de una accion. Para [ActionType.PLAY_CARD] transporta la
 * instancia a jugar y, opcionalmente, el avatar rival objetivo (como
 * [PlayerId], no una unidad).
 */
data class ActionPayload(
    val instanceId: InstanceId? = null,
    val targetPlayer: PlayerId? = null,
)

/**
 * Accion en cola del cliente (documento `actions/{autoId}` de ADR-009).
 *
 * Incluye la secuencia monotona [sequence] del cliente que el host usa para
 * validar el orden y la idempotencia.
 */
data class MatchAction(
    val playerId: PlayerId,
    val actionType: ActionType,
    val payload: ActionPayload = ActionPayload(),
    val sequence: Long,
)
