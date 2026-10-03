package com.cardclash.data.remote.sync

import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId

/**
 * Constantes de los campos del documento de metadata `matches/{matchId}`
 * (ADR-009).
 */
object MatchMetaFields {
    const val MATCH_ID = "matchId"
    const val HOST_ID = "hostId"
    const val CLIENT_ID = "clientId"
    const val MATCH_CODE = "matchCode"
    const val PHASE = "phase"
    const val TURN = "turn"
    const val CURRENT_PLAYER = "currentPlayer"
    const val WINNER = "winner"
    const val STATUS = "status"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
}

/**
 * Metadata completa de la partida (`matches/{matchId}`, ADR-009): campos
 * estaticos de identidad/union mas los campos jugables que el host refresca.
 *
 * Kotlin puro; la serializacion wire la hace [MatchMetadataCodec].
 */
data class MatchMetadata(
    val matchId: MatchId,
    val hostId: PlayerId,
    val clientId: PlayerId?,
    val matchCode: String,
    val phase: MatchSnapshot.Phase,
    val turn: Int,
    val currentPlayer: PlayerId,
    val winner: PlayerId?,
    val status: MatchStatus,
    val createdAt: Long,
    val updatedAt: Long,
)
