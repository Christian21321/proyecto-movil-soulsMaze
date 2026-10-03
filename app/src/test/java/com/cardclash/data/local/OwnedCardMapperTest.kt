package com.cardclash.data.local

import com.cardclash.data.local.entity.OwnedCardEntity
import com.cardclash.data.local.mapper.OwnedCardMapper
import com.cardclash.data.local.model.OwnedCard
import com.cardclash.domain.model.CardId
import com.cardclash.domain.progression.CardCollection
import com.cardclash.domain.progression.ProgressionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM (JUnit 4) del mapeo puro entidad <-> dominio de la coleccion.
 *
 * No tocan el runtime de Room: solo construyen data classes anotadas y
 * reconstruyen [CardCollection] del dominio.
 */
class OwnedCardMapperTest {

    // -----------------------------------------------------------------
    // toEntity / toOwnedCard
    // -----------------------------------------------------------------

    @Test
    fun toEntity_cardaIdYCopias_seMapeanACamposDeFila() {
        val entity = OwnedCardMapper.toEntity(CardId("attack-1"), 3)
        assertEquals("attack-1", entity.cardId)
        assertEquals(3, entity.ownedCount)
    }

    @Test
    fun toOwnedCard_mapeaFilaConCardIdYCopias() {
        val owned = OwnedCardMapper.toOwnedCard(OwnedCardEntity("heal-2", 4))
        assertEquals(CardId("heal-2"), owned.cardId)
        assertEquals(4, owned.ownedCount)
    }

    @Test
    fun toEntity_y_toOwnedCard_sonRoundTrip() {
        val original = OwnedCard(CardId("passive-max_mana-+1"), 2)
        val back = OwnedCardMapper.toOwnedCard(OwnedCardMapper.toEntity(original.cardId, original.ownedCount))
        assertEquals(original, back)
    }

    // -----------------------------------------------------------------
    // Reconstruccion de CardCollection
    // -----------------------------------------------------------------

    @Test
    fun toCollection_reconstruyeTenenciaYCartasPoseidas() {
        val entities = listOf(
            OwnedCardEntity("attack-0", 1),
            OwnedCardEntity("heal-1", 5),
        )
        val collection = OwnedCardMapper.toCollection(entities)

        assertEquals(1, collection.ownedCount(CardId("attack-0")))
        assertEquals(5, collection.ownedCount(CardId("heal-1")))
        assertEquals(0, collection.ownedCount(CardId("no-poseida")))
        assertEquals(setOf(CardId("attack-0"), CardId("heal-1")), collection.ownedCards())
    }

    @Test
    fun toCollection_vacia_devuelveColeccionVacia() {
        assertTrue(OwnedCardMapper.toCollection(emptyList()).ownedCards().isEmpty())
    }

    @Test
    fun fromCollection_proyectaColeccionADomain() {
        val rules = ProgressionRules()
        // Nivel 80: limite 5, podemos anadir 2 copias de "a" y 1 de "b".
        val collection = CardCollection()
            .addCopy(CardId("a"), rules, 80)
            .addCopy(CardId("a"), rules, 80)
            .addCopy(CardId("b"), rules, 80)

        val entities = OwnedCardMapper.fromCollection(collection)
        val byId = entities.associateBy { it.cardId }

        assertEquals(2, entities.size)
        assertEquals(2, byId["a"]?.ownedCount)
        assertEquals(1, byId["b"]?.ownedCount)
    }

    @Test
    fun roundTrip_coleccionEntidad_esEstable() {
        val rules = ProgressionRules()
        val collection = CardCollection()
            .addCopy(CardId("a"), rules, 1)
            .addCopy(CardId("b"), rules, 1)
            .addCopy(CardId("b"), rules, 1)

        val back = OwnedCardMapper.toCollection(OwnedCardMapper.fromCollection(collection))

        assertEquals(collection.ownedCards(), back.ownedCards())
        assertEquals(1, back.ownedCount(CardId("a")))
        assertEquals(2, back.ownedCount(CardId("b")))
    }
}