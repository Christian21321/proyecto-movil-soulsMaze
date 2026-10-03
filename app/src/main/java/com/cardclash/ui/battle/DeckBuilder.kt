package com.cardclash.ui.battle

import com.cardclash.domain.battle.MatchSessionController
import com.cardclash.domain.model.CardId
import com.cardclash.domain.repository.CardCatalog
import com.cardclash.domain.service.DeckRuleService

/**
 * Constructor PURO de mazos legales a partir de la colección poseída.
 *
 * Reglas de la demo (coherentes con [com.cardclash.domain.service.DeckRuleService]):
 * - A lo sumo [MAX_NON_PASSIVE] cartas NO pasivas distintas.
 * - A lo sumo [MAX_PASSIVE] cartas pasivas como extra.
 * - Si la colección no ofrece suficientes cartas, se cae al mazo por defecto de
 *   la demo ([MatchSessionController.defaultDemoDeck]).
 */
object DeckBuilder {

    const val MAX_NON_PASSIVE = 8
    const val MAX_PASSIVE = 1

    /**
     * Construye un mazo legal a partir de las cartas poseídas.
     *
     * @param ownedCards identidades de cartas con al menos una copia.
     * @param catalog catálogo para conocer el carácter pasivo de cada carta.
     */
    fun buildDeck(ownedCards: Set<CardId>, catalog: CardCatalog): List<CardId> {
        val owned = ownedCards.filter { catalog.findById(it) != null }.toList()
        if (owned.isEmpty()) return MatchSessionController.defaultDemoDeck()

        val nonPassive = owned.filter { catalog.findById(it)?.isPassive != true }.distinct()
        val passive = owned.filter { catalog.findById(it)?.isPassive == true }.distinct()

        val deck = nonPassive.take(MAX_NON_PASSIVE).toMutableList()
        if (deck.size < MAX_NON_PASSIVE) {
            deck += passive.take(MAX_PASSIVE)
        }
        return deck
    }

    /**
     * Mazo con el que se entra al combate.
     *
     * Usa [savedDeck] (el mazo activo o el último guardado en el Deck Builder)
     * si no está vacío, es legal según [DeckRuleService] y todas sus cartas
     * siguen en la colección [ownedCards]. Si no, cae al mazo automático de
     * [buildDeck].
     */
    fun battleDeck(savedDeck: List<CardId>?, ownedCards: Set<CardId>, catalog: CardCatalog): List<CardId> {
        val usable = !savedDeck.isNullOrEmpty() &&
            ownedCards.containsAll(savedDeck) &&
            DeckRuleService(catalog).validate(savedDeck).isOk
        return if (usable) savedDeck!! else buildDeck(ownedCards, catalog)
    }
}
