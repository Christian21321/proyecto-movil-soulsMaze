package com.cardclash.ui.history

import com.cardclash.data.local.model.MatchSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reducer puro (Kotlin/JVM, sin framework) de [MatchSummary] a [HistoryUiState].
 * Formatea la fecha con java.time (disponible en JVM y en Android API 26+).
 * Testeable con unit tests JVM pasando una zona fija.
 */
object HistoryMapper {

    /** Formato dd/MM/yyyy HH:mm con Locale.ROOT (determinista en tests). */
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)

    fun toUiState(
        matches: List<MatchSummary>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): HistoryUiState {
        val wins = matches.count { it.victory }
        return HistoryUiState(
            isLoading = false,
            entries = matches.map { toEntry(it, zoneId) },
            wins = wins,
            losses = matches.size - wins,
            totalXpEarned = matches.sumOf { it.xpEarned },
        )
    }

    fun toEntry(match: MatchSummary, zoneId: ZoneId = ZoneId.systemDefault()): HistoryEntryUi =
        HistoryEntryUi(
            matchId = match.matchId.value,
            victory = match.victory,
            xpEarned = match.xpEarned,
            playedAtLabel = Instant.ofEpochMilli(match.playedAt).atZone(zoneId).format(dateFormatter),
            summary = match.summary,
        )
}