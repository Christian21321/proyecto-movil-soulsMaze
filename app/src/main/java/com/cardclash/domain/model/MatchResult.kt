package com.cardclash.domain.model

/**
 * Resultado final de una partida de CardClash.
 *
 * Se produce cuando el motor detecta una condicion de victoria y pasa la
 * partida a [MatchSnapshot.Phase.FINISHED]. Encapsula quién ganó y por qué.
 */
data class MatchResult(
    val matchId: MatchId,
    val winner: PlayerId,
    val reason: WinReason,
    val finalSnapshot: MatchSnapshot,
)

/** Motivo por el que se declaró un vencedor. */
enum class WinReason {
    /** La salud del adversario alcanzó 0 o el adversario agotó sus recursos. */
    OPPONENT_DEFEATED,

    /** Condicion de victoria alternativa (p.ej. agotar cartas). */
    DECK_EXHAUSTED,
}
