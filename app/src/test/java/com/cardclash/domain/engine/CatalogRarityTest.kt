package com.cardclash.domain.engine

import com.cardclash.domain.model.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifica que [DefaultCardCatalog] asigna rareza a sus cartas y la expone via
 * [com.cardclash.domain.repository.CardCatalog.rarityOf], sin afectar al motor.
 */
class CatalogRarityTest {

    private val catalog = DefaultCardCatalog()

    @Test
    fun todasLasCartas_tienenRarezaDeclarada() {
        catalog.all().forEach { card ->
            assertNotNull("La carta ${card.id} deberia tener rareza", catalog.rarityOf(card.id))
        }
    }

    @Test
    fun rareza_coherenteConCosteDeMana() {
        catalog.all().forEach { card ->
            val rarity = catalog.rarityOf(card.id)!!
            // La rareza asignada coincide con la banda de coste definida en el catalogo:
            // >=5 -> SSR, ==4 -> SR, resto -> COMMON.
            val expected = when {
                card.cost >= 5 -> Rarity.SSR
                card.cost == 4 -> Rarity.SR
                else -> Rarity.COMMON
            }
            assertEquals("Rareza de ${card.id}", expected, rarity)
        }
    }

    @Test
    fun rarezaDeUnaCartaDesconocida_devuelveNull() {
        assertNull(catalog.rarityOf(com.cardclash.domain.model.CardId("no-existe")))
    }
}
