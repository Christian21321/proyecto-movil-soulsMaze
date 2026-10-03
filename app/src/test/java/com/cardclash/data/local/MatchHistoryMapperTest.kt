package com.cardclash.data.local

import com.cardclash.data.local.entity.MatchHistoryEntity
import com.cardclash.data.local.mapper.MatchHistoryMapper
import com.cardclash.data.local.model.MatchSummary
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.PlayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests JVM (JUnit 4) del mapeo puro entidad <-> resumen de partida.
 * No toca el runtime de Room.
 */
class MatchHistoryMapperTest {

    @Test
    fun toEntity_mapeaTodosLosCampos() {
        val entity = MatchHistoryMapper.toEntity(
            matchId = MatchId("m-1"),
            playerId = PlayerId("local-player"),
            victory = true,
            xpEarned = 5,
            playedAt = 1000L,
            summary = "Victoria contra P2",
        )

        assertEquals("m-1", entity.matchId)
        assertEquals("local-player", entity.playerId)
        assertEquals(true, entity.victory)
        assertEquals(5, entity.xpEarned)
        assertEquals(1000L, entity.playedAt)
        assertEquals("Victoria contra P2", entity.summary)
    }

    @Test
    fun toSummary_mapeaCamposYResumenOpcional() {
        val entity = MatchHistoryEntity(
            matchId = "m-2",
            playerId = "local-player",
            victory = false,
            xpEarned = 2,
            playedAt = 2000L,
            summary = null,
        )

        val summary = MatchHistoryMapper.toSummary(entity)

        assertEquals(MatchId("m-2"), summary.matchId)
        assertEquals(PlayerId("local-player"), summary.playerId)
        assertEquals(false, summary.victory)
        assertEquals(2, summary.xpEarned)
        assertEquals(2000L, summary.playedAt)
        assertNull(summary.summary)
    }

    @Test
    fun roundTrip_entidadYResumen_esEstable() {
        val entity = MatchHistoryEntity(
            matchId = "m-3",
            playerId = "local-player",
            victory = true,
            xpEarned = 5,
            playedAt = 3000L,
            summary = "Aplastante",
        )
        val summary = MatchHistoryMapper.toSummary(entity)
        val back = MatchHistoryMapper.toEntity(
            matchId = summary.matchId,
            playerId = summary.playerId,
            victory = summary.victory,
            xpEarned = summary.xpEarned,
            playedAt = summary.playedAt,
            summary = summary.summary,
        )

        assertEquals(entity, back)
        assertEquals(MatchSummary(MatchId("m-3"), PlayerId("local-player"), true, 5, 3000L, "Aplastante"), summary)
    }

    @Test
    fun toEntity_y_toSummary_preservanResultadoDerrota() {
        val entity = MatchHistoryMapper.toEntity(
            MatchId("m-4"), PlayerId("local-player"), victory = false, xpEarned = 2, playedAt = 4000L, summary = null,
        )
        assertEquals(false, MatchHistoryMapper.toSummary(entity).victory)
        assertEquals(2, MatchHistoryMapper.toSummary(entity).xpEarned)
    }
}