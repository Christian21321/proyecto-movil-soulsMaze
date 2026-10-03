package com.cardclash.domain.service

import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.UnitStat
import com.cardclash.domain.repository.CardCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de las reglas de mazo ALINEADAS al modelo consolidado (Etapa 1):
 * deck maximo de 8 cartas NO pasivas, sin duplicados (max 1 copia) y a lo
 * sumo 1 carta pasiva MAX_MANA como extra.
 */
class DeckRuleServiceTest {

    private val attack1 = Card(CardId("atk-1"), "Ataque 1", cost = 3, attack = 4, maxHealth = 6)
    private val attack2 = Card(CardId("atk-2"), "Ataque 2", cost = 3, attack = 5, maxHealth = 5)
    private val attack3 = Card(CardId("atk-3"), "Ataque 3", cost = 4, attack = 6, maxHealth = 6)
    private val attack4 = Card(CardId("atk-4"), "Ataque 4", cost = 4, attack = 5, maxHealth = 7)
    private val attack5 = Card(CardId("atk-5"), "Ataque 5", cost = 5, attack = 8, maxHealth = 8)
    private val attack6 = Card(CardId("atk-6"), "Ataque 6", cost = 3, attack = 4, maxHealth = 5)
    private val attack7 = Card(CardId("atk-7"), "Ataque 7", cost = 3, attack = 5, maxHealth = 6)
    private val attack8 = Card(CardId("atk-8"), "Ataque 8", cost = 4, attack = 6, maxHealth = 7)
    private val attack9 = Card(CardId("atk-9"), "Ataque 9", cost = 5, attack = 9, maxHealth = 9)

    private val passivaMana = Card(
        CardId("passive-mana-1"), "Aura de mana", cost = 4, attack = 0, maxHealth = 2,
        passiveStat = UnitStat.MAX_MANA, passiveAmount = 2, isPassive = true,
    )
    private val pasiva2 = Card(
        CardId("passive-mana-2"), "Aura de mana 2", cost = 4, attack = 0, maxHealth = 2,
        passiveStat = UnitStat.MAX_MANA, passiveAmount = 1, isPassive = true,
    )

    private val catalog: CardCatalog = object : CardCatalog {
        private val map = (listOf(
            attack1, attack2, attack3, attack4, attack5, attack6, attack7, attack8, attack9,
            passivaMana, pasiva2,
        )).associateBy { it.id }
        override fun findById(id: CardId): Card? = map[id]
        override fun all(): List<Card> = map.values.toList()
    }

    private val service = DeckRuleService(catalog)

    private fun ids(vararg cards: Card): List<CardId> = cards.map { it.id }

    @Test
    fun deckDe8CartasNoPasivas_valido() {
        val deck = ids(attack1, attack2, attack3, attack4, attack5, attack6, attack7, attack8)
        val result = service.validate(deck)
        assertTrue(result.isOk)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun deckDe9CartasNoPasivas_invalido() {
        val deck = ids(attack1, attack2, attack3, attack4, attack5, attack6, attack7, attack8, attack9)
        val result = service.validate(deck)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("no pasivas") })
    }

    @Test
    fun deckDe8Mas1Pasiva_valido_comoExtra() {
        val deck = ids(attack1, attack2, attack3, attack4, attack5, attack6, attack7, attack8) + passivaMana.id
        val result = service.validate(deck)
        assertTrue(result.isOk)
    }

    @Test
    fun deckConDuplicados_invalido() {
        val deck = ids(attack1, attack1, attack2, attack3, attack4, attack5, attack6, attack7)
        val result = service.validate(deck)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("máx 1") })
    }

    @Test
    fun deckCon2Pasivas_invalido() {
        val deck = ids(attack1, attack2, attack3, attack4, attack5, attack6, attack7, attack8) +
            passivaMana.id + pasiva2.id
        val result = service.validate(deck)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("pasivas") })
    }

    @Test
    fun deckConCartaDesconocida_invalido() {
        val deck = ids(attack1, attack2, attack3, attack4, attack5, attack6, attack7) +
            CardId("no-existe")
        val result = service.validate(deck)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("desconocidas") })
    }
}
