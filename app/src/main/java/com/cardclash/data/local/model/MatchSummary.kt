package com.cardclash.data.local.model

import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.PlayerId

/**
 * Resumen de una partida del historial (valor inmutable, Kotlin puro).
 *
 * Es el mapeo de dominio de [com.cardclash.data.local.entity.MatchHistoryEntity]:
 * identificador, resultado, XP otorgado, momento y resumen breve opcional.
 */
data class MatchSummary(
    val matchId: MatchId,
    val playerId: PlayerId,
    val victory: Boolean,
    val xpEarned: Int,
    val playedAt: Long,
    val summary: String?,
)