package com.cardclash.ui.deckbuilder

import com.cardclash.data.local.model.Deck
import com.cardclash.data.local.model.DeckCard
import com.cardclash.data.local.model.PlayerProgress
import com.cardclash.data.local.entity.PlayerProfileEntity
import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.Rarity
import com.cardclash.domain.progression.CardCollection
import com.cardclash.domain.progression.ProgressionRules
import com.cardclash.domain.repository.CardCatalog
import com.cardclash.domain.service.DeckRuleService

object DeckBuilderMapper {

    fun toUiModel(
        deck: Deck,
        collection: CardCollection,
        catalog: CardCatalog,
        playerLevel: Int,
        rules: ProgressionRules
    ): DeckUiModel {
        val slots = buildSlots(deck, catalog)
        val nonPassiveCount = slots.count { !it.isPassive && it.cardId != null }
        val passiveCount = slots.count { it.isPassive && it.cardId != null }
        val totalCopies = slots.count { it.cardId != null }
        return DeckUiModel(
            id = deck.id.value,
            name = deck.name,
            slots = slots,
            nonPassiveCount = nonPassiveCount,
            passiveCount = passiveCount,
            totalCopiesInDeck = totalCopies,
            isModified = false
        )
    }

    fun toCollectionCardUi(
        card: Card,
        ownedCopies: Int,
        maxCopiesAtLevel: Int,
        isFavorite: Boolean = false
    ): CollectionCardUi {
        val rarity = Rarity.COMMON
        val canAdd = ownedCopies < maxCopiesAtLevel
        val typeFilter = getTypeFilter(card)
        return CollectionCardUi(
            cardId = card.id,
            name = card.name,
            rarity = rarity,
            manaCost = card.cost,
            copiesOwned = ownedCopies,
            maxCopies = maxCopiesAtLevel,
            canAdd = canAdd,
            isPassive = card.isPassive,
            isCompatible = true,
            isFavorite = isFavorite,
            effectDescription = describeEffect(card)
        )
    }

    fun toManaCurveData(
        deckUi: DeckUiModel,
        catalog: CardCatalog
    ): ManaCurveData {
        val cardsWithCost: List<Pair<Int, Rarity>> = deckUi.slots
            .filter { it.cardId != null }
            .mapNotNull { slot ->
                slot.cardId?.let { cid ->
                    catalog.findById(cid)?.let { card ->
                        card.cost to (catalog.rarityOf(cid) ?: Rarity.COMMON)
                    }
                }
            }
        val byCost = cardsWithCost.groupBy { it.first }.mapValues { (_, v) -> v.size }
        val byCostAndRarity = cardsWithCost
            .groupBy { it.first }
            .mapValues { (_, v) -> v.groupBy { it.second }.mapValues { (_, v2) -> v2.size } }
        val passiveCount = deckUi.slots.count { it.isPassive && it.cardId != null }
        return ManaCurveData(
            totalCards = deckUi.totalCopiesInDeck,
            byCost = byCost,
            byCostAndRarity = byCostAndRarity,
            passiveCount = passiveCount
        )
    }

    fun toDomain(deckUi: DeckUiModel): Deck {
        val deckId = deckUi.id?.let { com.cardclash.data.local.model.DeckId(it) }
            ?: com.cardclash.data.local.model.DeckId(java.util.UUID.randomUUID().toString())
        val cards = deckUi.slots
            .filter { it.cardId != null }
            .map { DeckCard(it.index, it.cardId!!) }
        return Deck(
            id = deckId,
            name = deckUi.name,
            isActive = false,
            playerId = com.cardclash.domain.model.PlayerId(PlayerProfileEntity.DEFAULT_PLAYER_ID),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            cards = cards
        )
    }

    private fun buildSlots(deck: Deck, catalog: CardCatalog): List<DeckSlotUi> {
        val cardBySlot = deck.cards.associateBy({ it.slot }, { it.cardId })
        return (0..8).map { index ->
            val cid = cardBySlot[index]
            if (cid != null) {
                val card = catalog.findById(cid)
                val rarity = catalog.rarityOf(cid) ?: Rarity.COMMON
                val isPassive = card?.isPassive == true
                DeckSlotUi(
                    index = index,
                    cardId = cid,
                    name = card?.name,
                    rarity = rarity,
                    manaCost = card?.cost,
                    isPassive = isPassive,
                    state = SlotValidationStatus.Filled
                )
            } else {
                DeckSlotUi(index = index, state = SlotValidationStatus.Empty)
            }
        }
    }

    private fun getTypeFilter(card: Card): CardTypeFilter = when (card.effect) {
        is com.cardclash.domain.model.CardEffect.Attack -> CardTypeFilter.ATTACK
        is com.cardclash.domain.model.CardEffect.Heal -> CardTypeFilter.HEAL
        is com.cardclash.domain.model.CardEffect.Draw -> CardTypeFilter.DRAW
        is com.cardclash.domain.model.CardEffect.ApplyStatus -> CardTypeFilter.STATUS
        is com.cardclash.domain.model.CardEffect.PassiveBuff -> CardTypeFilter.PASSIVE
        else -> CardTypeFilter.ALL
    }

    private fun describeEffect(card: Card): String = when (card.effect) {
        is com.cardclash.domain.model.CardEffect.Attack -> "Daño ${card.effect.amount}"
        is com.cardclash.domain.model.CardEffect.Heal -> "Cura ${card.effect.amount}"
        is com.cardclash.domain.model.CardEffect.Draw -> "Roba ${card.effect.count}"
        is com.cardclash.domain.model.CardEffect.ApplyStatus -> "${card.effect.status.name} ${card.effect.durationTurns}t"
        is com.cardclash.domain.model.CardEffect.PassiveBuff -> "Pasiva +${card.effect.amount}"
        else -> ""
    }
}