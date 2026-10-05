package com.cardclash.domain.progression

import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de progresion y coleccion de CardClash.
 *
 * Cubren la derivacion nivel/XP (con limites de tramo 40/41, 50/51, 60/61,
 * 70/71, 80), el limite de copias por tramo, el desbloqueo consumiendo 1 copia
 * por tramo (T2-T5), la curva de XP y la coleccion de cartas con limits.
 */
class ProgressionTest {

    private val rules = ProgressionRules()

    private fun card(id: String) = Card(
        CardId(id), "Carta $id", cost = 3, attack = 3, maxHealth = 5,
    )

    // ---------------------------------------------------------------------
    // Curva de niveles / XP
    // ---------------------------------------------------------------------

    @Test
    fun level1_parteConXpCero() {
        assertEquals(1, rules.levelForXp(0))
        assertEquals(0, rules.curve.xpIntoLevel(0))
    }

    @Test
    fun xpAcumulada_derivaBienVariosNiveles() {
        // 25 XP por nivel en los niveles 1-10.
        assertEquals(2, rules.levelForXp(25))
        assertEquals(5, rules.levelForXp(100))
        assertEquals(11, rules.levelForXp(250))
        // 11-20 cuestan 50: 250 + 50 = 300 -> nivel 12.
        assertEquals(12, rules.levelForXp(300))
    }

    @Test
    fun xpForLevel_nivel1EsCero_y_acumulaCorrecto() {
        assertEquals(0, rules.curve.xpForLevel(1))
        assertEquals(25, rules.curve.xpForLevel(2))
        assertEquals(10 * 25, rules.curve.xpForLevel(11))
        assertEquals(10 * 25 + 10 * 50, rules.curve.xpForLevel(21))
        assertEquals(10 * 25 + 10 * 50 + 10 * 75, rules.curve.xpForLevel(31))
    }

    @Test
    fun xpPerLevel_t1EmpiezaBaratoYSubeHasta100() {
        assertEquals(25, rules.curve.xpPerLevel(1))
        assertEquals(25, rules.curve.xpPerLevel(10))
        assertEquals(50, rules.curve.xpPerLevel(11))
        assertEquals(75, rules.curve.xpPerLevel(21))
        assertEquals(100, rules.curve.xpPerLevel(31))
        assertEquals(100, rules.curve.xpPerLevel(40))
        assertEquals(150, rules.curve.xpPerLevel(41))
        // Cada nivel cuesta al menos lo mismo que el anterior.
        (2..LevelCurve.MAX_LEVEL).forEach { level ->
            assertTrue(rules.curve.xpPerLevel(level) >= rules.curve.xpPerLevel(level - 1))
        }
    }

    @Test
    fun limitesDeTramo_niveles40_41_50_51_60_61_70_71_80() {
        // T1 (1-40): 10*25 + 10*50 + 10*75 + 10*100 = 2500 para llegar al 41.
        val xp41 = 10 * 25 + 10 * 50 + 10 * 75 + 10 * 100
        assertEquals(40, rules.levelForXp(xp41 - 100))
        assertEquals(40, rules.levelForXp(xp41 - 1))
        assertEquals(41, rules.levelForXp(xp41))
        assertEquals(42, rules.levelForXp(xp41 + 150))

        // T3 inicia en 51: + 10*150 (T2).
        val xp51 = xp41 + 10 * 150
        assertEquals(50, rules.levelForXp(xp51 - 150))
        assertEquals(51, rules.levelForXp(xp51))

        // T4 inicia en 61: + 10*200.
        val xp61 = xp51 + 10 * 200
        assertEquals(60, rules.levelForXp(xp61 - 200))
        assertEquals(61, rules.levelForXp(xp61))

        // T5 inicia en 71: + 10*300.
        val xp71 = xp61 + 10 * 300
        assertEquals(70, rules.levelForXp(xp71 - 300))
        assertEquals(71, rules.levelForXp(xp71))

        // Nivel 80 (tope): + 9*400.
        val xp80 = xp71 + 9 * 400
        assertEquals(79, rules.levelForXp(xp80 - 1))
        assertEquals(80, rules.levelForXp(xp80))
        // Mas alla del maximo, el nivel se topa en 80 (XP sin techo).
        assertEquals(80, rules.levelForXp(xp80 + 99999))
    }

    @Test
    fun levelForXp_coincideConXpForLevelEnTodosLosNiveles() {
        (1..LevelCurve.MAX_LEVEL).forEach { level ->
            val xp = rules.curve.xpForLevel(level)
            assertEquals(level, rules.levelForXp(xp))
            if (level > 1) assertEquals(level - 1, rules.levelForXp(xp - 1))
        }
    }

    @Test
    fun xpIntoLevel_sobranteDentroDelNivel() {
        // 60 XP -> nivel 3 (50 acumulado) y 10 dentro del nivel 3.
        assertEquals(3, rules.levelForXp(60))
        assertEquals(10, rules.curve.xpIntoLevel(60))
        // Con XP exacta para nivel 4 (75), sobrante 0.
        assertEquals(0, rules.curve.xpIntoLevel(75))
    }

    // ---------------------------------------------------------------------
    // Tramos
    // ---------------------------------------------------------------------

    @Test
    fun tierForLevel_bordesExactos() {
        assertEquals(Tier.T1, rules.tierForLevel(1))
        assertEquals(Tier.T1, rules.tierForLevel(40))
        assertEquals(Tier.T2, rules.tierForLevel(41))
        assertEquals(Tier.T2, rules.tierForLevel(50))
        assertEquals(Tier.T3, rules.tierForLevel(51))
        assertEquals(Tier.T3, rules.tierForLevel(60))
        assertEquals(Tier.T4, rules.tierForLevel(61))
        assertEquals(Tier.T4, rules.tierForLevel(70))
        assertEquals(Tier.T5, rules.tierForLevel(71))
        assertEquals(Tier.T5, rules.tierForLevel(80))
    }

    @Test
    fun tierForLevel_fueraDeRango_lanza() {
        assertThrows(IllegalArgumentException::class.java) { rules.tierForLevel(0) }
        assertThrows(IllegalArgumentException::class.java) { rules.tierForLevel(81) }
    }

    // ---------------------------------------------------------------------
    // Limite de copias por tramo (desbloqueo consumiendo 1 copia)
    // ---------------------------------------------------------------------

    @Test
    fun limiteDeCopias_porTramo() {
        assertEquals(2, rules.maxCopiesAllowed(1))
        assertEquals(2, rules.maxCopiesAllowed(40))
        assertEquals(3, rules.maxCopiesAllowed(41))
        assertEquals(3, rules.maxCopiesAllowed(50))
        assertEquals(4, rules.maxCopiesAllowed(51))
        assertEquals(4, rules.maxCopiesAllowed(60))
        assertEquals(5, rules.maxCopiesAllowed(61))
        assertEquals(5, rules.maxCopiesAllowed(70))
        assertEquals(5, rules.maxCopiesAllowed(71)) // T5 tope contractual
        assertEquals(5, rules.maxCopiesAllowed(80))
    }

    @Test
    fun copiasConsumidas_porTramo_unDosTresCuatro() {
        assertEquals(0, rules.copiesConsumedForTier(1))
        assertEquals(0, rules.copiesConsumedForTier(40))
        assertEquals(1, rules.copiesConsumedForTier(41))
        assertEquals(2, rules.copiesConsumedForTier(51))
        assertEquals(3, rules.copiesConsumedForTier(61))
        assertEquals(4, rules.copiesConsumedForTier(71))
        assertEquals(4, rules.copiesConsumedForTier(80))
    }

    @Test
    fun canAddCopy_respetaLimiteDelTramo() {
        val c = card("a")
        // Nivel T1 (limite 2): con 2 copias ya no puede anadir una mas.
        assertTrue(rules.canAddCopy(c, 1, 5))
        assertFalse(rules.canAddCopy(c, 2, 5))
        // Nivel T2 (limite 3): con 3 copias no puede anadir mas.
        assertFalse(rules.canAddCopy(c, 3, 45))
        assertTrue(rules.canAddCopy(c, 2, 45))
        // Nivel T4/T5 (limite 5): con 5 copias topadas no puede anadir mas.
        assertFalse(rules.canAddCopy(c, 5, 80))
        assertTrue(rules.canAddCopy(c, 4, 80))
    }

    // ---------------------------------------------------------------------
    // Otorgar XP
    // ---------------------------------------------------------------------

    @Test
    fun grantXp_fuentes() {
        assertEquals(5, rules.grantXp(0, XpSource.VICTORY))
        assertEquals(2, rules.grantXp(0, XpSource.DEFEAT))
        assertEquals(2, rules.grantXp(0, XpSource.SHOP))
        // Acumulacion: 100 + 5 victoria + 2 derrota = 107.
        assertEquals(107, rules.grantXp(rules.grantXp(100, XpSource.VICTORY), XpSource.DEFEAT))
    }

    @Test
    fun grantShopXp_max2_y_validaCeroNegativoMayor() {
        assertEquals(100, rules.grantShopXp(100, 0))
        assertEquals(102, rules.grantShopXp(100, 2))
        assertThrows(IllegalArgumentException::class.java) { rules.grantShopXp(100, 3) }
        assertThrows(IllegalArgumentException::class.java) { rules.grantShopXp(100, -1) }
    }

    // ---------------------------------------------------------------------
    // Coleccion
    // ---------------------------------------------------------------------

    @Test
    fun coleccion_operacionesBasicas() {
        val col = CardCollection()
        val c = CardId("a")
        assertEquals(0, col.ownedCount(c))
        // A nivel 1 (limite 2) podemos anadir 2 copias.
        val c1 = col.addCopy(c, rules, 1)
        val c2 = c1.addCopy(c, rules, 1)
        assertEquals(2, c2.ownedCount(c))
        // Eliminar una vuelve a 1.
        val c3 = c2.removeCopy(c)
        assertEquals(1, c3.ownedCount(c))
    }

    @Test
    fun coleccion_limitePorNivel_bloqueaMasCopias() {
        val col = CardCollection()
        val c = CardId("a")
        // T1 limite 2 -> la 3a copia a nivel 40 es invalida.
        var actual = col
        actual = actual.addCopy(c, rules, 40)
        actual = actual.addCopy(c, rules, 40)
        assertThrows(IllegalArgumentException::class.java) { actual.addCopy(c, rules, 40) }
        // A nivel 41 (T2 limite 3) se puede anadir la 3a.
        val t2 = actual.addCopy(c, rules, 41)
        assertEquals(3, t2.ownedCount(c))
    }

    @Test
    fun coleccion_removeSinCopias_lanza() {
        val col = CardCollection()
        assertThrows(IllegalArgumentException::class.java) {
            col.removeCopy(CardId("no-existe"))
        }
    }

    @Test
    fun coleccion_ownedCards_soloConCopias() {
        val col = CardCollection()
            .addCopy(CardId("a"), rules, 1)
            .addCopy(CardId("b"), rules, 1)
        assertEquals(setOf(CardId("a"), CardId("b")), col.ownedCards())
    }

    // ---------------------------------------------------------------------
    // Rareza
    // ---------------------------------------------------------------------

    @Test
    fun rarity_costeDeMana() {
        assertEquals(3, Rarity.COMMON.manaCost)
        assertEquals(4, Rarity.SR.manaCost)
        assertEquals(5, Rarity.SSR.manaCost)
    }
}
