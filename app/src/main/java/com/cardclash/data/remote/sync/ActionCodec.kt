package com.cardclash.data.remote.sync

import com.cardclash.data.remote.dto.IdCodec
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.PlayerId
import com.cardclash.data.remote.sync.ActionFields.ACTION_TYPE
import com.cardclash.data.remote.sync.ActionFields.INSTANCE_ID
import com.cardclash.data.remote.sync.ActionFields.PLAYER_ID
import com.cardclash.data.remote.sync.ActionFields.SEQUENCE
import com.cardclash.data.remote.sync.ActionFields.TARGET_PLAYER

/**
 * Codificacion de una [MatchAction] hacia/desde el wire del documento
 * `actions/{autoId}` (ADR-009). Kotlin puro: el adaptador Firestore solo
 * traduce entre `Map` y este objeto.
 */
object ActionCodec {

    fun toWire(action: MatchAction): Map<String, Any?> = mapOf(
        PLAYER_ID to action.playerId.value,
        ACTION_TYPE to action.actionType.name,
        SEQUENCE to action.sequence,
        INSTANCE_ID to action.payload.instanceId?.value,
        TARGET_PLAYER to action.payload.targetPlayer?.value,
    )

    fun fromWire(map: Map<String, Any?>): MatchAction {
        val instanceId = IdCodec.rehydrate<InstanceId>(map[INSTANCE_ID] as? String)
        val targetPlayer = IdCodec.rehydrate<PlayerId>(map[TARGET_PLAYER] as? String)
        return MatchAction(
            playerId = IdCodec.rehydrate<PlayerId>(map[PLAYER_ID] as? String)
                ?: error("Campo '${PLAYER_ID}' ausente en la accion del wire."),
            actionType = ActionType.valueOf(map[ACTION_TYPE] as String),
            payload = ActionPayload(instanceId = instanceId, targetPlayer = targetPlayer),
            sequence = (map[SEQUENCE] as? Number)?.toLong()
                ?: error("Campo '${SEQUENCE}' ausente en la accion del wire."),
        )
    }
}
