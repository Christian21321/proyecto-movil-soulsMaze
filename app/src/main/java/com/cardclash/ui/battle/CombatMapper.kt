package com.cardclash.ui.battle

import com.cardclash.domain.battle.CombatSessionState
import com.cardclash.domain.battle.CombatUiState
import com.cardclash.domain.battle.SessionRole
import com.cardclash.domain.battle.SessionStatus
import com.cardclash.domain.model.MatchSnapshot

/**
 * Mapper PURO de la sesión de combate hacia las etiquetas de la pantalla.
 *
 * Convierte un [CombatSessionState] (rol/estado + [CombatUiState] opcional) en
 * presentación textual lista para Compose (turno, fase, turno actual, mana).
 * Es una función pura (misma entrada ⇒ misma salida) y se testea en JVM sin
 * tocar Android/ViewModel.
 */
object CombatMapper {

    /** Etiqueta legible de la fase de la partida. */
    fun phaseLabel(phase: MatchSnapshot.Phase): String = when (phase) {
        MatchSnapshot.Phase.PREPARING -> "Preparación"
        MatchSnapshot.Phase.PLAYING -> "En curso"
        MatchSnapshot.Phase.FINISHED -> "Finalizada"
    }

    /** Etiqueta de en quién es el turno, desde el punto de vista del jugador local. */
    fun turnLabel(ui: CombatUiState): String =
        if (ui.isMyTurn) "Tu turno" else "Turno del rival"

    /** Texto del turno actual, 1-based. */
    fun turnNumberLabel(turn: Int): String = "Turno $turn"

    /** Etiqueta de mana del jugador local ("actual / máximo"). */
    fun manaLabel(actual: Int, max: Int): String = "$actual / $max"

    /** Conteo de cartas del rival. */
    fun opponentHandLabel(size: Int): String = "Cartas en mano: $size"

    /** Resumen textual del estado de sesión (conexión/error/terminada). */
    fun statusLabel(status: SessionStatus): String = when (status) {
        SessionStatus.IDLE -> "Sin partida"
        SessionStatus.CONNECTING -> "Conectando…"
        SessionStatus.ACTIVE -> "En partida"
        SessionStatus.FINISHED -> "Partida finalizada"
        SessionStatus.ERROR -> "Error"
    }

    /** Etiqueta del rol de la sesión, o null si aún no se eligió. */
    fun roleLabel(role: SessionRole?): String = when (role) {
        SessionRole.HOST -> "Anfitrión"
        SessionRole.CLIENT -> "Cliente"
        SessionRole.LOCAL_SOLO -> "Local"
        null -> "—"
    }

    /** Estado completo de presentación a partir de la sesión. */
    fun toLabels(session: CombatSessionState): CombatLabels {
        val ui = session.uiState
        return CombatLabels(
            phaseLabel = ui?.phase?.let(::phaseLabel) ?: "—",
            turnLabel = ui?.let(::turnLabel) ?: statusLabel(session.status),
            turnNumberLabel = ui?.let { turnNumberLabel(it.turn) } ?: "—",
            manaLabel = ui?.let { manaLabel(it.myMana, it.maxMana) } ?: "—",
            opponentHandLabel = ui?.let { opponentHandLabel(it.opponentHandSize) } ?: "—",
            statusLabel = statusLabel(session.status),
            roleLabel = roleLabel(session.role),
        )
    }
}

/** Presentación textual derivada de la sesión de combate (inmutable). */
data class CombatLabels(
    val phaseLabel: String,
    val turnLabel: String,
    val turnNumberLabel: String,
    val manaLabel: String,
    val opponentHandLabel: String,
    val statusLabel: String,
    val roleLabel: String,
)
