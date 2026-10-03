package com.cardclash.data.remote.sync

import com.cardclash.data.remote.dto.IdCodec
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.data.remote.sync.MatchMetaFields.CLIENT_ID
import com.cardclash.data.remote.sync.MatchMetaFields.CREATED_AT
import com.cardclash.data.remote.sync.MatchMetaFields.CURRENT_PLAYER
import com.cardclash.data.remote.sync.MatchMetaFields.HOST_ID
import com.cardclash.data.remote.sync.MatchMetaFields.MATCH_CODE
import com.cardclash.data.remote.sync.MatchMetaFields.MATCH_ID
import com.cardclash.data.remote.sync.MatchMetaFields.PHASE
import com.cardclash.data.remote.sync.MatchMetaFields.STATUS
import com.cardclash.data.remote.sync.MatchMetaFields.TURN
import com.cardclash.data.remote.sync.MatchMetaFields.UPDATED_AT
import com.cardclash.data.remote.sync.MatchMetaFields.WINNER

/**
 * Serializacion de la metadata `matches/{matchId}` hacia/desde el wire
 * (ADR-009). Kotlin puro, testeable en JVM.
 */
object MatchMetadataCodec {

    fun toWire(meta: MatchMetadata): Map<String, Any?> = mapOf(
        MATCH_ID to meta.matchId.value,
        HOST_ID to meta.hostId.value,
        CLIENT_ID to meta.clientId?.value,
        MATCH_CODE to meta.matchCode,
        PHASE to meta.phase.name,
        TURN to meta.turn.toLong(),
        CURRENT_PLAYER to meta.currentPlayer.value,
        WINNER to meta.winner?.value,
        STATUS to meta.status.name,
        CREATED_AT to meta.createdAt,
        UPDATED_AT to meta.updatedAt,
    )

    fun fromWire(map: Map<String, Any?>): MatchMetadata = MatchMetadata(
        matchId = IdCodec.rehydrate<MatchId>(requireString(map, MATCH_ID))!!,
        hostId = IdCodec.rehydrate<PlayerId>(requireString(map, HOST_ID))
            ?: error("Campo '$HOST_ID' ausente en la metadata del wire."),
        clientId = IdCodec.rehydrate<PlayerId>(map[CLIENT_ID] as? String),
        matchCode = requireString(map, MATCH_CODE),
        phase = MatchSnapshot.Phase.valueOf(requireString(map, PHASE)),
        turn = (map[TURN] as? Number)?.toInt() ?: 0,
        currentPlayer = IdCodec.rehydrate<PlayerId>(requireString(map, CURRENT_PLAYER))
            ?: error("Campo '$CURRENT_PLAYER' ausente en la metadata del wire."),
        winner = (map[WINNER] as? String)?.let { PlayerId(it) },
        status = MatchStatus.valueOf(requireString(map, STATUS)),
        createdAt = (map[CREATED_AT] as? Number)?.toLong() ?: 0L,
        updatedAt = (map[UPDATED_AT] as? Number)?.toLong() ?: 0L,
    )

    private fun requireString(map: Map<String, Any?>, key: String): String =
        map[key] as? String ?: error("Campo '$key' ausente en la metadata del wire.")
}
