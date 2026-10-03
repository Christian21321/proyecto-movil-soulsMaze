package com.cardclash.ui.menuhome

import com.cardclash.data.local.mapper.PlayerProgressMapper
import com.cardclash.domain.model.CardId
import com.cardclash.domain.progression.CardCollection
import com.cardclash.domain.progression.ProgressionRules
import com.cardclash.domain.progression.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests JVM (JUnit 4) del reducer puro [HomeMapper]: PlayerProgress +
 * CardCollection -> HomeUiState (resumen del menu).
 */
class HomeMapperTest {

    private val rules = ProgressionRules()
    private val mapper = HomeMapper

    private fun progress(xp: Int) = PlayerProgressMapper.deriveProgress("P1", xp, 0L, rules)

    private fun collection(vararg pairs: Pair<String, Int>): CardCollection =
        CardCollection(pairs.associate { CardId(it.first) to it.second })

    @Test
    fun estadoInicial_sinXpNiCartas() {
        val state = mapper.toUiState(progress(0), collection(), rules)
        assertEquals(1, state.level)
        assertEquals(Tier.T1, state.tier)
        assertEquals(0, state.xpTotal)
        assertEquals(0, state.xpIntoLevel)
        assertEquals(100, state.xpNeededForNextLevel)
        assertEquals(0f, state.xpProgressFraction)
        assertEquals(0, state.collectionSize)
        assertEquals(0, state.totalCopies)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun estadoConXp_derivaNivelYTramo() {
        val state = mapper.toUiState(progress(4000), collection(), rules)
        assertEquals(41, state.level)
        assertEquals(Tier.T2, state.tier)
        assertEquals(4000, state.xpTotal)
        assertEquals(0, state.xpIntoLevel)
        assertEquals(4150, state.xpNeededForNextLevel)
        assertEquals(0f, state.xpProgressFraction)
    }

    @Test
    fun estadoAMitadDeNivel_fraccionCorrecta() {
        val state = mapper.toUiState(progress(150), collection(), rules)
        assertEquals(2, state.level)
        assertEquals(50, state.xpIntoLevel)
        assertEquals(0.5f, state.xpProgressFraction)
    }

    @Test
    fun nivelMaximo_sinSiguienteYFraccionLlena() {
        val state = mapper.toUiState(progress(14100), collection(), rules)
        assertEquals(80, state.level)
        assertEquals(Tier.T5, state.tier)
        assertNull(state.xpNeededForNextLevel)
        assertEquals(1f, state.xpProgressFraction)
    }

    @Test
    fun coleccion_cuentaCartasDistintasYTotales() {
        val state = mapper.toUiState(progress(0), collection("attack-0" to 3, "heal-0" to 1), rules)
        assertEquals(2, state.collectionSize)
        assertEquals(4, state.totalCopies)
    }

    @Test
    fun coleccionConCuatroCopiaDeUnaCarta_noCuentaLaCartaDosVeces() {
        val state = mapper.toUiState(progress(0), collection("attack-0" to 4), rules)
        assertEquals(1, state.collectionSize)
        assertEquals(4, state.totalCopies)
    }
}