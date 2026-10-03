package com.cardclash.data

import com.cardclash.domain.battle.MatchResultSink
import com.cardclash.domain.model.MatchId

/**
 * Implementación del puerto [MatchResultSink] sobre el repositorio local.
 *
 * Recibe el desenlace que calcula el controlador puro de combate (victoria +5
 * XP / derrota +2 XP) y lo persiste en una MISMA transacción Room mediante
 * [PlayerProgressRepository.recordMatch] (otorga XP y escribe el histórico).
 *
 * El controlador puro entrega [xpEarned] ya calculado; esta capa solo delega en
 * el repositorio para garantizar coherencia (XP + histórico atómicos).
 */
class LocalMatchResultSink(
    private val repository: PlayerProgressRepository,
) : MatchResultSink {

    override suspend fun recordMatch(
        matchId: MatchId,
        victory: Boolean,
        xpEarned: Int,
        summary: String,
    ) {
        // El xpEarned lo aporta el controlador; el repositorio recalcula la
        // fuente (victoria/derrota) y persiste ambos efectos en una transacción.
        repository.recordMatch(matchId = matchId, victory = victory, summary = summary)
    }
}
