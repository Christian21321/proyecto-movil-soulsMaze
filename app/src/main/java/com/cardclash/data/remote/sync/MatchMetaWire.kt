package com.cardclash.data.remote.sync

import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId

/**
 * Estado global de la partida en la metadata `matches/{matchId}` (ADR-009).
 */
enum class MatchStatus {
    /** Partida creada pero el rival aun no se ha unido. */
    WAITING,

    /** Partida en curso con ambos jugadores conectados. */
    ACTIVE,

    /** Partida terminada con [MatchMetaWire.winner] resuelto. */
    FINISHED,

    /** Partida abandonada. */
    ABANDONED,
}

/**
 * Campos actualizables de la metadata `matches/{matchId}` que el host calcula
 * tras procesar una accion (ADR-009). Los campos estaticos (hostId, clientId,
 * matchCode, createdAt) los fija la creacion/union de la partida.
 */
data class MatchMetaWire(
    val matchId: MatchId,
    val phase: MatchSnapshot.Phase,
    val turn: Int,
    val currentPlayer: PlayerId,
    val winner: PlayerId?,
    val status: MatchStatus,
) {
    companion object {

        /** Deriva la metadata actualizable desde un [snapshot] tras una accion. */
        fun fromSnapshot(snapshot: MatchSnapshot): MatchMetaWire = MatchMetaWire(
            matchId = snapshot.matchId,
            phase = snapshot.phase,
            turn = snapshot.turn,
            currentPlayer = snapshot.currentPlayer,
            winner = snapshot.winner,
            status = if (snapshot.phase == MatchSnapshot.Phase.FINISHED) {
                MatchStatus.FINISHED
            } else {
                MatchStatus.ACTIVE
            },
        )
    }
}
