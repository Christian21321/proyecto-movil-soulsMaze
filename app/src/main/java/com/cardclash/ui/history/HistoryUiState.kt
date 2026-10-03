package com.cardclash.ui.history

/**
 * Entrada de historial lista para UI: resultado, XP ganado y fecha ya formateada.
 */
data class HistoryEntryUi(
    val matchId: String,
    val victory: Boolean,
    val xpEarned: Int,
    val playedAtLabel: String,
    val summary: String?,
)

/**
 * Estado UI del historial (UDF): entradas mas recientes primero + resumen de
 * victorias/derrotas/XP acumulado.
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<HistoryEntryUi> = emptyList(),
    val wins: Int = 0,
    val losses: Int = 0,
    val totalXpEarned: Int = 0,
)