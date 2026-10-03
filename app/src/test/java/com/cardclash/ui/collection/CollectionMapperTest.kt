package com.cardclash.ui.collection

import com.cardclash.data.local.mapper.PlayerProgressMapper
import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.Rarity
import com.cardclash.domain.progression.CardCollection
import com.cardclash.domain.progression.ProgressionRules
import com.cardclash.domain.progression.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM (JUnit 4) del reducer puro [CollectionMapper]: fusion de la
 * coleccion poseida con el catalogo real [DefaultCardCatalog].
 *
 * El catalogo contiene 18 cartas; la rareza se deriva del coste (5+=SSR, 4=SR,
 * resto COMMON). Limites por tramo: T1=2, T2=3, T3=4, T4/T5=5 (tope).
 */
class CollectionMapperTest {

    private val rules = ProgressionRules()
    private val catalog = DefaultCardCatalog()
    private val mapper = CollectionMapper

    private fun progress(xp: Int) = PlayerProgressMapper.deriveProgress("P1", xp, 0L, rules)

    private fun collection(vararg pairs: Pair<String, Int>): CardCollection =
        CardCollection(pairs.associate { CardId(it.first) to it.second })

    private fun card(state: CollectionUiState, id: String): CollectionCardUi =
        state.cards.first { it.cardId.value == id }

    @Test
    fun listaTodasLasCartasDelCatalogo_conRarezaYCosteDeMana() {
        val state = mapper.toUiState(progress(0), collection(), catalog, rules)

        assertEquals(18, state.cards.size)
        assertEquals(1, state.level)
        assertEquals(Tier.T1, state.tier)
        assertEquals(2, state.maxCopiesAllowed)

        // Coste de mana derivado de la rareza (convencion 3/4/5).
        val golpe = card(state, "attack-0")
        assertEquals("Golpe 0", golpe.name)
        assertEquals(Rarity.COMMON, golpe.rarity)
        assertEquals(3, golpe.manaCost)

        val veneno = card(state, "status-poison")
        assertEquals(Rarity.SR, veneno.rarity)
        assertEquals(4, veneno.manaCost)

        val escarcha = card(state, "status-frost")
        assertEquals(Rarity.SSR, escarcha.rarity)
        assertEquals(5, escarcha.manaCost)
    }

    @Test
    fun cartasNoPoseidas_copiasCeroSinEliminar() {
        val state = mapper.toUiState(progress(0), collection(), catalog, rules)
        val golpe = card(state, "attack-0")
        assertEquals(0, golpe.copiesOwned)
        assertFalse(golpe.canRemove)
        assertTrue(golpe.canAdd)
    }

    @Test
    fun cartasPoseidas_reflejanCopiasYFlags() {
        val state = mapper.toUiState(progress(0), collection("attack-0" to 1), catalog, rules)
        val golpe = card(state, "attack-0")
        assertEquals(1, golpe.copiesOwned)
        assertEquals(2, golpe.maxCopies)
        assertTrue(golpe.canAdd)
        assertTrue(golpe.canRemove)
    }

    @Test
    fun limiteDeCopias_derivaDelTramo() {
        // T1 (nivel 40): limite 2; con 2 copias no se puede anadir mas.
        val t1 = mapper.toUiState(progress(3900), collection("attack-0" to 2), catalog, rules)
        assertEquals(2, t1.maxCopiesAllowed)
        assertFalse(card(t1, "attack-0").canAdd)

        // T2 (nivel 41): limite 3; con 2 copias aun se puede anadir.
        val t2 = mapper.toUiState(progress(4000), collection("attack-0" to 2), catalog, rules)
        assertEquals(3, t2.maxCopiesAllowed)
        assertTrue(card(t2, "attack-0").canAdd)

        // T3 (nivel 51): limite 4.
        val t3 = mapper.toUiState(progress(5500), collection(), catalog, rules)
        assertEquals(4, t3.maxCopiesAllowed)
        assertEquals(Tier.T3, t3.tier)

        // T5 (nivel 80): limite contratual 5.
        val t5 = mapper.toUiState(progress(14100), collection(), catalog, rules)
        assertEquals(5, t5.maxCopiesAllowed)
        assertEquals(Tier.T5, t5.tier)
    }

    @Test
    fun limiteAlcanzado_noPermiteAnadirPeroSiEliminar() {
        val state = mapper.toUiState(progress(3900), collection("attack-0" to 2), catalog, rules)
        val golpe = card(state, "attack-0")
        assertFalse(golpe.canAdd)
        assertTrue(golpe.canRemove)
    }

    @Test
    fun listaOrdenada_porRarezaYNombre() {
        val state = mapper.toUiState(progress(0), collection(), catalog, rules)
        val rarities = state.cards.map { it.rarity.ordinal }
        // Ordinales de rareza no decrecientes (COMMON < SR < SSR).
        assertEquals(rarities.sorted(), rarities)
        // Dentro de la misma rareza, nombres ordenados alfabeticamente.
        state.cards.groupBy { it.rarity }.values.forEach { group ->
            assertEquals(group.map { it.name }.sorted(), group.map { it.name })
        }
        // La primera carta es COMMON y al final estan las SSR.
        assertEquals(Rarity.COMMON, state.cards.first().rarity)
        assertEquals(Rarity.SSR, state.cards.last().rarity)
    }
}