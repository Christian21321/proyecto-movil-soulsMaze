package com.cardclash.ui.deckbuilder

import com.cardclash.data.local.model.Deck
import com.cardclash.data.local.model.DeckCard
import com.cardclash.data.local.model.DeckId
import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.progression.CardCollection
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CardTypeFilter
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.FilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests JVM de la lógica pura del constructor de mazos ([DeckBuilderMapper]). */
class DeckBuilderMapperTest {

    private val catalog = DefaultCardCatalog()
    private val mapper = DeckBuilderMapper
    private val passive = CardId("passive-max_mana-+2")
    private val normals = listOf(
        "attack-0", "attack-1", "attack-2", "attack-3",
        "heal-0", "heal-1", "draw-0", "status-burn",
    ).map(::CardId)

    private fun ownedAll(): CardCollection =
        CardCollection(catalog.all().associate { it.id to 1 })

    @Test
    fun mazoVacio_tiene8HuecosNormalesY1Pasivo() {
        val deck = mapper.emptyDeck()

        assertEquals(9, deck.slots.size)
        assertEquals(8, deck.slots.count { !it.isPassiveSlot })
        assertTrue(deck.slots[DeckBuilderUiState.PASSIVE_SLOT_INDEX].isPassiveSlot)
        assertTrue(deck.slots.all { it.isEmpty })
    }

    @Test
    fun addCard_colocaLaPasivaEnSuHuecoYLasNormalesEnOrden() {
        val deck = mapper.deckWith(listOf(passive) + normals.take(2), catalog)

        assertEquals(passive, deck.slots[8].cardId)
        assertEquals(normals[0], deck.slots[0].cardId)
        assertEquals(normals[1], deck.slots[1].cardId)
        assertEquals(2, deck.nonPassiveCount)
        assertEquals(1, deck.passiveCount)
    }

    @Test
    fun addCard_ignoraDuplicadosYMazoLleno() {
        val full = mapper.deckWith(normals, catalog)
        val extra = catalog.findById(CardId("attack-4"))!!
        val repeated = catalog.findById(normals[0])!!

        assertEquals(full.slots, mapper.addCard(full, extra, catalog).slots)
        assertEquals(full.slots, mapper.addCard(full, repeated, catalog).slots)
    }

    @Test
    fun removeCard_vaciaElHuecoYMarcaModificado() {
        val deck = mapper.removeCard(mapper.deckWith(normals.take(3), catalog), 1)

        assertNull(deck.slots[1].cardId)
        assertEquals(2, deck.nonPassiveCount)
        assertTrue(deck.isModified)
    }

    @Test
    fun ida_y_vuelta_dominio_conservaCartasYId() {
        val ui = mapper.deckWith(normals.take(3) + passive, catalog, name = "  Fuego  ")
        val domain = mapper.toDomain(ui, PlayerId("local-player"), now = 10L)

        assertEquals("Fuego", domain.name)
        assertEquals(4, domain.cards.size)
        val back = mapper.toUiModel(domain, catalog)
        assertEquals(domain.id.value, back.id)
        assertEquals(ui.cardIds, back.cardIds)
    }

    @Test
    fun toUiModel_mueveLaPasivaAlHueco8() {
        val id = DeckId.generate()
        val deck = Deck(
            id = id,
            name = "Guardado",
            isActive = true,
            playerId = PlayerId("local-player"),
            createdAt = 1L,
            updatedAt = 1L,
            cards = listOf(DeckCard(0, passive), DeckCard(1, normals[0])),
        )

        val ui = mapper.toUiModel(deck, catalog)

        assertEquals(normals[0], ui.slots[0].cardId)
        assertEquals(passive, ui.slots[8].cardId)
    }

    @Test
    fun validate_exigeNombreCartasYPosesion() {
        val empty = mapper.validate(mapper.emptyDeck(name = " "), ownedAll(), catalog)
        assertFalse(empty.isValid)
        assertEquals(2, empty.errors.size)

        val deck = mapper.deckWith(normals.take(2), catalog)
        val notOwned = mapper.validate(deck, CardCollection(mapOf(normals[0] to 1)), catalog)
        assertFalse(notOwned.isValid)
        assertEquals(setOf(1), notOwned.slotsWithErrors)

        assertTrue(mapper.validate(deck, ownedAll(), catalog).isValid)
    }

    @Test
    fun collectionCards_soloPermiteAnadirPoseidasFueraDelMazoConHueco() {
        val deck = mapper.deckWith(normals.take(1), catalog)
        val owned = CardCollection(mapOf(normals[0] to 1, normals[1] to 1))

        val cards = mapper.collectionCards(deck, owned, catalog).associateBy { it.cardId }

        assertEquals(catalog.all().size, cards.size)
        assertFalse(cards.getValue(normals[0]).canAdd)
        assertTrue(cards.getValue(normals[0]).inDeck)
        assertTrue(cards.getValue(normals[1]).canAdd)
        assertFalse(cards.getValue(normals[2]).canAdd)
    }

    @Test
    fun applyFilter_porTipoYPoseidas() {
        val owned = CardCollection(mapOf(normals[0] to 1, CardId("heal-0") to 1))
        val cards = mapper.collectionCards(mapper.emptyDeck(), owned, catalog)

        val onlyOwned = mapper.applyFilter(cards, FilterState())
        assertEquals(2, onlyOwned.size)

        val heals = mapper.applyFilter(cards, FilterState(types = setOf(CardTypeFilter.HEAL), onlyOwned = false))
        assertEquals(3, heals.size)
        assertTrue(heals.all { it.type == CardTypeFilter.HEAL })
    }

    @Test
    fun manaCurve_cuentaSoloCartasNormalesPorCoste() {
        val deck = mapper.deckWith(normals.take(3) + passive, catalog)

        val curve = mapper.manaCurve(deck)

        // attack-0/1 cuestan 1 y attack-2 cuesta 2; la pasiva no cuenta.
        assertEquals(mapOf(1 to 2, 2 to 1), curve.byCost)
        assertEquals(2, curve.maxCount)
    }
}
