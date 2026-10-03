package com.cardclash.ui.common

import com.cardclash.data.local.mapper.PlayerProgressMapper
import com.cardclash.domain.progression.ProgressionRules
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests JVM (JUnit 4) de la fraccion de progreso compartida entre pantallas.
 *
 * Fronteras de la curva usadas: nivel 40=3900, 41=4000, 50=5350, 51=5500,
 * 60=7300, 61=7500, 70=10200, 71=10500, 80=14100 (XP total para ALCANZAR el
 * nivel; XP por nivel T1=100, T2=150, T3=200, T4=300, T5=400).
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
        // 100 XP -> nivel 2 recien alcanzado: 0 dentro del nivel.
        assertEquals(0f, ProgressUiMapper.progressFraction(progress(100), rules))
    }

    @Test
    fun xpAMitadDeTramoT1_fraccionMedio() {
        // 150 XP -> nivel 2 con 50 XP dentro de un nivel que exige 100 (T1).
        assertEquals(0.5f, ProgressUiMapper.progressFraction(progress(150), rules))
    }

    @Test
    fun xpCasiSubeDeNivel_fraccionCasiCompleta() {
        // 3999 XP -> nivel 40 con 99/100 (T1).
        assertEquals(0.99f, ProgressUiMapper.progressFraction(progress(3999), rules))
    }

    @Test
    fun xpEnTramoSuperior_usaXpPorNivelDelTramo() {
        // 5499 XP -> nivel 50 (T2) con 149 de 150.
        val fraction = ProgressUiMapper.progressFraction(progress(5499), rules)
        assertEquals(149f / 150f, fraction, 0.0001f)
    }

    @Test
    fun nivelMaximo_fraccionCompleta() {
        // 14100 XP -> nivel 80 (sin siguiente nivel): barra llena.
        assertEquals(1f, ProgressUiMapper.progressFraction(progress(14100), rules))
        // XP sin techo se topa en nivel 80: fraccion sigue siendo 1.
        assertEquals(1f, ProgressUiMapper.progressFraction(progress(14100 + 999_999), rules))
    }
}