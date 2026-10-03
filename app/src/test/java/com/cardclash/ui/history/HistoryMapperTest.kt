package com.cardclash.ui.history

import com.cardclash.data.local.model.MatchSummary
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.PlayerId
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM (JUnit 4) del reducer puro [HistoryMapper]: MatchSummary ->
 * HistoryUiState con fecha formateada en zona fija (determinista).
 */
class HistoryMapperTest {

    private val zone = ZoneId.of("UTC")
    private val mapper = HistoryMapper

    private val t0 = Instant.parse("2026-08-28T10:15:00Z").toEpochMilli()
    private val t1 = Instant.parse("2026-08-27T21:05:00Z").toEpochMilli()

    private fun match(
        id: String,
        victory: Boolean,
        xp: Int,
        summary: String? = null,
        playedAt: Long = t0,
    ) = MatchSummary(MatchId(id), PlayerId("P1"), victory, xp, playedAt, summary)

    @Test
    fun historialVacio_cerosYSinEntradas() {
        val state = mapper.toUiState(emptyList(), zone)
        assertTrue(state.entries.isEmpty())
        assertEquals(0, state.wins)
        assertEquals(0, state.losses)
        assertEquals(0, state.totalXpEarned)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun resumen_cuentaVictoriasDerrotasYXP() {
        val state = mapper.toUiState(
            listOf(match("m1", victory = true, xp = 5), match("m2", victory = false, xp = 2), match("m3", victory = true, xp = 5)),
            zone,
        )
        assertEquals(3, state.entries.size)
        assertEquals(2, state.wins)
        assertEquals(1, state.losses)
        assertEquals(12, state.totalXpEarned)
    }

    @Test
    fun fecha_formateadaEnZonaFija() {
        val state = mapper.toUiState(listOf(match("m1", victory = true, xp = 5, playedAt = t0)), zone)
        assertEquals("28/08/2026 10:15", state.entries.single().playedAtLabel)
    }

    @Test
    fun fecha_otraHoraIgualaConSuFormato() {
        val state = mapper.toUiState(listOf(match("m1", victory = true, xp = 5, playedAt = t1)), zone)
        assertEquals("27/08/2026 21:05", state.entries.single().playedAtLabel)
    }

    @Test
    fun entrada_propagaResultadoXpYResumen() {
        val state = mapper.toUiState(listOf(match("m1", victory = true, xp = 5, summary = "Victoria por KO")), zone)
        val entry = state.entries.single()
        assertEquals("m1", entry.matchId)
        assertTrue(entry.victory)
        assertEquals(5, entry.xpEarned)
        assertEquals("Victoria por KO", entry.summary)
    }

    @Test
    fun entrada_sinResumen_devuelveNull() {
        val state = mapper.toUiState(listOf(match("m1", victory = false, xp = 2)), zone)
        assertNull(state.entries.single().summary)
    }

    @Test
    fun toEntry_mapeaCamposIndividuales() {
        val entry = mapper.toEntry(match("m9", victory = false, xp = 2, playedAt = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()), zone)
        assertEquals("m9", entry.matchId)
        assertEquals(false, entry.victory)
        assertEquals(2, entry.xpEarned)
        assertEquals("01/01/2026 00:00", entry.playedAtLabel)
    }
}