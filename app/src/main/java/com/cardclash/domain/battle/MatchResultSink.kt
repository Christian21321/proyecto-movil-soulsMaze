package com.cardclash.domain.battle

import com.cardclash.domain.model.MatchId

/**
 * Puerto de salida para persistir el resultado de una partida en el histórico
 * local — NO implementado aquí.
 *
 * La implementación real (capa `data`) lo conectará al
 * [com.cardclash.data.PlayerProgressRepository.recordMatch] (otorga XP y
 * registra la partida en una misma transacción Room).
 *
 * El controlador puro calcula y entrega [victory] y [xpEarned] (victoria +5,
 * derrota +2 según [com.cardclash.domain.progression.XpSource]) para que esta
 * regla sea testeable en JVM; el sumidero solo se encarga de persistir.
 */
interface MatchResultSink {

    /**
     * Registra un resultado terminado en el histórico.
     *
     * @param matchId identidad de la partida finalizada.
     * @param victory true si el jugador local ganó.
     * @param xpEarned XP a conceder por el desenlace (5 victoria / 2 derrota).
     * @param summary resumen legible del desenlace (p. ej. "Victoria por aniquilación").
     */
    suspend fun recordMatch(
        matchId: MatchId,
        victory: Boolean,
        xpEarned: Int,
        summary: String,
    ): Unit
}
