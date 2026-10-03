package com.cardclash.data.local.mapper

import com.cardclash.data.local.entity.MatchHistoryEntity
import com.cardclash.data.local.model.MatchSummary
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.PlayerId

/**
 * Mapeos puros entidad Room <-> resumen de partida.
 *
 * Kotlin PURO (sin runtime Room): se testea con unit tests JVM
 * ([com.cardclash.data.local.MatchHistoryMapperTest]).
 */
object MatchHistoryMapper {

    /** Construye la fila Room de una partida registrada. */
    fun toEntity(
        matchId: MatchId,
        playerId: PlayerId,
        victory: Boolean,
        xpEarned: Int,
        playedAt: Long,
        summary: String?,
    ): MatchHistoryEntity =
        MatchHistoryEntity(
            matchId = matchId.value,
            playerId = playerId.value,
            victory = victory,
            xpEarned = xpEarned,
            playedAt = playedAt,
            summary = summary,
        )

    /** Convierte una fila Room al resumen de dominio. */
    fun toSummary(entity: MatchHistoryEntity): MatchSummary =
        MatchSummary(
            matchId = MatchId(entity.matchId),
            playerId = PlayerId(entity.playerId),
            victory = entity.victory,
            xpEarned = entity.xpEarned,
            playedAt = entity.playedAt,
            summary = entity.summary,
        )
}