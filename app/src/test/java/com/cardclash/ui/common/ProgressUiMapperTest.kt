package com.cardclash.ui.common

import com.cardclash.data.local.mapper.PlayerProgressMapper
import com.cardclash.domain.progression.ProgressionRules
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests JVM (JUnit 4) de la fraccion de progreso compartida entre pantallas.
 *
 * Fronteras de la curva usadas: nivel 2=25, 11=250, 40=2400, 41=2500,
 * 50=3850, 51=4000, 80=12600 (XP total para ALCANZAR el nivel; XP por nivel
 * T1=25/50/75/100 por decenas, T2=150, T3=200, T4=300, T5=400).
 */
class ProgressUiMapperTest {

    private val rules = ProgressionRules()

    private fun progress(xp: Int) = PlayerProgressMapper.deriveProgress("P1", xp, 0L, rules)

    @Test
    fun xpCero_fraccionCero() {
        assertEquals(0f, ProgressUiMapper.progressFraction(progress(0), rules))
    }

    @Test
    fun xpJustoEnNivel_fraccionCero() {
        // 25 XP -> nivel 2 recien alcanzado: 0 dentro del nivel.
        assertEquals(0f, ProgressUiMapper.progressFraction(progress(25), rules))
    }

    @Test
    fun xpAMitadDeTramoT1_fraccionMedio() {
        // 275 XP -> nivel 11 con 25 XP dentro de un nivel que exige 50.
        assertEquals(0.5f, ProgressUiMapper.progressFraction(progress(275), rules))
    }

    @Test
    fun xpCasiSubeDeNivel_fraccionCasiCompleta() {
        // 2499 XP -> nivel 40 con 99/100 (T1).
        assertEquals(0.99f, ProgressUiMapper.progressFraction(progress(2499), rules))
    }

    @Test
    fun xpEnTramoSuperior_usaXpPorNivelDelTramo() {
        // 3999 XP -> nivel 50 (T2) con 149 de 150.
        val fraction = ProgressUiMapper.progressFraction(progress(3999), rules)
        assertEquals(149f / 150f, fraction, 0.0001f)
    }

    @Test
    fun nivelMaximo_fraccionCompleta() {
        // 12600 XP -> nivel 80 (sin siguiente nivel): barra llena.
        assertEquals(1f, ProgressUiMapper.progressFraction(progress(12600), rules))
        // XP sin techo se topa en nivel 80: fraccion sigue siendo 1.
        assertEquals(1f, ProgressUiMapper.progressFraction(progress(12600 + 999_999), rules))
    }
}