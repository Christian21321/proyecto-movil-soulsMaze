package com.cardclash.ui.battle

import com.cardclash.domain.battle.MatchSessionController
import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.model.CardId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM (JUnit 4) del constructor PURO de mazos [DeckBuilder]: derivación de
 * un mazo legal desde la colección poseída.
 */
class DeckBuilderTest {

    private val catalog = DefaultCardCatalog()

    // attack-0..5 (no pasivas), heal-0..2 (no pasivas), draw-0..2 (no pasivas),
    // status-* (no pasivas), passive-* (pasivas).
    private fun cardIds(vararg names: String): Set<CardId> =
        names.map(::CardId).toSet()

    @Test
    fun coleccionVacia_caeAlMazoDeDemo() {
        val deck = DeckBuilder.buildDeck(emptySet(), catalog)
        assertEquals(MatchSessionController.defaultDemoDeck(), deck)
    }

    @Test
    fun coleccionConCartaUsaLasCartasPropias() {
        val deck = DeckBuilder.buildDeck(cardIds("attack-0"), catalog)
        assertTrue(deck.contains(CardId("attack-0")))
        assertTrue(deck.size <= DeckBuilder.MAX_NON_PASSIVE + DeckBuilder.MAX_PASSIVE)
    }

    @Test
    fun noIncluyeCartasDesconocidas() {
        val deck = DeckBuilder.buildDeck(cardIds("no-existe-0", "attack-1"), catalog)
        assertTrue(deck.contains(CardId("attack-1")))
        assertTrue(deck.none { it.value == "no-existe-0" })
    }

    @Test
    fun limitaAVeroconstMasUnaPasiva() {
        val many = cardIds("attack-0", "attack-1", "attack-2", "attack-3", "attack-4", "attack-5",
            "heal-0", "heal-1", "heal-2", "draw-0", "status-frost", "passive-max_mana-+2")
        val deck = DeckBuilder.buildDeck(many, catalog)
        val nonPassive = deck.count { catalog.findById(it)?.isPassive != true }
        val passive = deck.count { catalog.findById(it)?.isPassive == true }
        assertTrue(nonPassive <= DeckBuilder.MAX_NON_PASSIVE)
        assertTrue(passive <= DeckBuilder.MAX_PASSIVE)
    }

    // ---------------------------------------------------------------------
    // battleDeck: mazo guardado vs mazo automático
    // ---------------------------------------------------------------------

    @Test
    fun battleDeck_usaElMazoGuardadoSiEsLegalYPoseido() {
        val saved = listOf("attack-0", "heal-1", "status-burn").map(::CardId)
        val owned = cardIds("attack-0", "attack-1", "heal-1", "status-burn")
        assertEquals(saved, DeckBuilder.battleDeck(saved, owned, catalog))
    }

    @Test
    fun battleDeck_sinMazoGuardado_caeAlAutomatico() {
        val owned = cardIds("attack-0", "attack-1")
        assertEquals(DeckBuilder.buildDeck(owned, catalog), DeckBuilder.battleDeck(null, owned, catalog))
        assertEquals(DeckBuilder.buildDeck(owned, catalog), DeckBuilder.battleDeck(emptyList(), owned, catalog))
    }

    @Test
    fun battleDeck_conCartasNoPoseidas_caeAlAutomatico() {
        val saved = listOf("attack-0", "attack-5").map(::CardId)
        val owned = cardIds("attack-0")
        assertEquals(DeckBuilder.buildDeck(owned, catalog), DeckBuilder.battleDeck(saved, owned, catalog))
    }

    @Test
    fun battleDeck_ilegal_caeAlAutomatico() {
        // Dos copias de la misma carta: DeckRuleService solo permite 1.
        val saved = listOf("attack-0", "attack-0").map(::CardId)
        val owned = cardIds("attack-0")
        assertEquals(DeckBuilder.buildDeck(owned, catalog), DeckBuilder.battleDeck(saved, owned, catalog))
    }
}
