package com.cardclash.data.remote.dto

import com.cardclash.domain.model.ActiveStatus
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.StatusDecay
import com.cardclash.domain.model.StatusInstanceId
import com.cardclash.domain.model.StatusType
import com.cardclash.data.remote.dto.WireFields.DECAY
import com.cardclash.data.remote.dto.WireFields.DURATION_TURNS
import com.cardclash.data.remote.dto.WireFields.PLAYER_ID
import com.cardclash.data.remote.dto.WireFields.STATUS_INSTANCE_ID
import com.cardclash.data.remote.dto.WireFields.STATUS_TYPE
import com.cardclash.data.remote.dto.WireFields.TURNS_REMAINING

/**
 * Serializacion de un [ActiveStatus] hacia/desde su representacion wire
 * (Map<String, Any?>).
 *
 * Tras la Fase 2a los estados viven sobre el AVATAR de un jugador, por lo que
 * el campo que identifica al portador es [ActiveStatus.playerId] (un
 * [PlayerId]), ya no un identificador de unidad.
 */
internal object ActiveStatusCodec {

    fun toWire(status: ActiveStatus): Map<String, Any?> = mapOf(
        STATUS_INSTANCE_ID to status.instanceId.value,
        STATUS_TYPE to status.type.name,
        PLAYER_ID to status.playerId.value,
        DURATION_TURNS to status.durationTurns.toLong(),
        TURNS_REMAINING to status.turnsRemaining.toLong(),
        DECAY to status.decay.name,
    )

    fun fromWire(map: Map<String, Any?>): ActiveStatus {
        val type = StatusType.valueOf(WireRead.requireStr(map, STATUS_TYPE))
        val playerId = IdCodec.rehydrate<PlayerId>(WireRead.requireStr(map, PLAYER_ID))
            ?: error("Campo '$PLAYER_ID' ausente en el wire del estado activo.")
        return ActiveStatus(
            instanceId = IdCodec.rehydrate<StatusInstanceId>(
                WireRead.requireStr(map, STATUS_INSTANCE_ID),
            ) ?: error("Campo '$STATUS_INSTANCE_ID' ausente en el wire del estado activo."),
            type = type,
            playerId = playerId,
            durationTurns = WireRead.int(map, DURATION_TURNS),
            turnsRemaining = WireRead.int(map, TURNS_REMAINING),
            decay = StatusDecay.valueOf(WireRead.requireStr(map, DECAY)),
        )
    }
}
