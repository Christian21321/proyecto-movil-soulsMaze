package com.cardclash.data.local

import com.cardclash.data.local.entity.PlayerProfileEntity
import com.cardclash.data.local.mapper.PlayerProgressMapper
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.progression.ProgressionRules
import com.cardclash.domain.progression.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests JVM (JUnit 4) de la derivacion de progresion desde XP total y de los
 * otorgamientos de XP. Usa [ProgressionRules]/[LevelCurve] del dominio; no toca
 * el runtime de Room.
 *
 * Fronteras de la curva (XP acumulada para ALCANZAR el nivel):
 * nivel 40 = 2400, nivel 41 = 2500, nivel 50 = 3850, nivel 51 = 4000,
 * nivel 60 = 5800, nivel 61 = 6000, nivel 70 = 8700, nivel 71 = 9000,
 * nivel 80 = 12600.
 */
class PlayerProgressMapperTest {

    private val rules = ProgressionRules()
    private val mapper = PlayerProgressMapper

    private fun derive(xp: Int, playerId: String = PlayerProfileEntity.DEFAULT_PLAYER_ID, updatedAt: Long = 0L) =
        mapper.deriveProgress(playerId, xp, updatedAt, rules)

    // -----------------------------------------------------------------
    // Derivacion nivel / tramo / XP-sobrante
    // -----------------------------------------------------------------

    @Test
    fun xpCero_nivel1T1_max2Copias_siguiente25() {
        val p = derive(0)
        assertEquals(1, p.level)
        assertEquals(Tier.T1, p.tier)
        assertEquals(0, p.xpIntoLevel)
        assertEquals(25, p.xpNeededForNextLevel)
        assertEquals(2, p.maxCopiesAllowed)
        assertEquals(0, p.xpTotal)
    }

    @Test
    fun frontera40_41_xp2499ysubida() {
        // 2499 XP -> nivel 40 (T1) con 99 XP dentro; requiere 2500 para el 41.
        val antes = derive(2499)
        assertEquals(40, antes.level)
        assertEquals(Tier.T1, antes.tier)
        assertEquals(99, antes.xpIntoLevel)
        assertEquals(2500, antes.xpNeededForNextLevel)
        assertEquals(2, antes.maxCopiesAllowed)

        // 2500 XP -> nivel 41 (T2), desbloquea limite 3.
        val despues = derive(2500)
        assertEquals(41, despues.level)
        assertEquals(Tier.T2, despues.tier)
        assertEquals(0, despues.xpIntoLevel)
        assertEquals(3, despues.maxCopiesAllowed)
    }

    @Test
    fun frontera50_51_seMantieneT2LuegoT3() {
        // 3850 -> nivel 50 (T2, max 3); 3999 -> 50 con 149 dentro.
        val n50 = derive(3850)
        assertEquals(50, n50.level)
        assertEquals(Tier.T2, n50.tier)
        assertEquals(0, n50.xpIntoLevel)
        assertEquals(3, n50.maxCopiesAllowed)

        val n50b = derive(3999)
        assertEquals(50, n50b.level)
        assertEquals(149, n50b.xpIntoLevel)
        assertEquals(4000, n50b.xpNeededForNextLevel)

        // 4000 -> nivel 51 (T3, max 4).
        val n51 = derive(4000)
        assertEquals(51, n51.level)
        assertEquals(Tier.T3, n51.tier)
        assertEquals(0, n51.xpIntoLevel)
        assertEquals(4, n51.maxCopiesAllowed)
    }

    @Test
    fun frontera60_61_y70_71() {
        // 5800 -> 60 (T3); 6000 -> 61 (T4, max 5).
        assertEquals(60, derive(5800).level)
        assertEquals(61, derive(6000).level)
        assertEquals(Tier.T4, derive(6000).tier)
        assertEquals(5, derive(6000).maxCopiesAllowed)

        // 8700 -> 70 (T4); 8999 -> 70 con 299 dentro; 9000 -> 71 (T5).
        assertEquals(70, derive(8700).level)
        val t4Tope = derive(8999)
        assertEquals(70, t4Tope.level)
        assertEquals(299, t4Tope.xpIntoLevel)
        assertEquals(9000, t4Tope.xpNeededForNextLevel)
        assertEquals(Tier.T5, derive(9000).tier)
        assertEquals(71, derive(9000).level)
    }

    @Test
    fun nivel80_topeYMuyAlto_deXp() {
        // 12600 -> nivel 80, sin siguiente nivel, maximo contractual 5.
        val tope = derive(12600)
        assertEquals(80, tope.level)
        assertEquals(Tier.T5, tope.tier)
        assertEquals(0, tope.xpIntoLevel)
        assertNull(tope.xpNeededForNextLevel)
        assertEquals(5, tope.maxCopiesAllowed)

        // XP sin techo: el nivel se topa en 80 y el sobrante sigue acumulandose.
        val muyAlto = derive(12600 + 999_999)
        assertEquals(80, muyAlto.level)
        assertEquals(999_999, muyAlto.xpIntoLevel)
        assertEquals(5, muyAlto.maxCopiesAllowed)
    }

    @Test
    fun toProgress_desdeEntidad_derivaLoMismoQueDeriveProgress() {
        val entity = PlayerProfileEntity(PlayerProfileEntity.DEFAULT_PLAYER_ID, 4000, 1234L)
        val p = mapper.toProgress(entity, rules)
        assertEquals(PlayerId(PlayerProfileEntity.DEFAULT_PLAYER_ID), p.playerId)
        assertEquals(51, p.level)
        assertEquals(Tier.T3, p.tier)
        assertEquals(0, p.xpIntoLevel)
        assertEquals(1234L, p.updatedAt)
        assertEquals(rules.levelForXp(4000), p.level)
    }

    @Test
    fun toEntity_mapeaCampos() {
        val entity = mapper.toEntity(PlayerId("local-player"), 4000, 42L)
        assertEquals("local-player", entity.playerId)
        assertEquals(4000, entity.xpTotal)
        assertEquals(42L, entity.updatedAt)
    }

    // -----------------------------------------------------------------
    // Otorgar XP (victoria/derrota/tienda)
    // -----------------------------------------------------------------

    @Test
    fun grantVictory_suma5() {
        assertEquals(5, mapper.grantVictory(0, rules))
        assertEquals(105, mapper.grantVictory(100, rules))
        assertEquals(14105, mapper.grantVictory(14100, rules))
    }

    @Test
    fun grantDefeat_suma2() {
        assertEquals(2, mapper.grantDefeat(0, rules))
        assertEquals(102, mapper.grantDefeat(100, rules))
    }

    @Test
    fun grantShop_respetaTope2() {
        assertEquals(100, mapper.grantShop(100, 0, rules))
        assertEquals(102, mapper.grantShop(100, 2, rules))
        // Acumulacion: 100 + 5 victoria + 2 derrota + 2 tienda = 109.
        val trasPartidas = mapper.grantVictory(mapper.grantDefeat(100, rules), rules)
        assertEquals(109, mapper.grantShop(trasPartidas, 2, rules))
    }

    @Test
    fun grantShop_fueraDeRango_lanza() {
        assertThrows(IllegalArgumentException::class.java) { mapper.grantShop(100, 3, rules) }
        assertThrows(IllegalArgumentException::class.java) { mapper.grantShop(100, -1, rules) }
    }
}