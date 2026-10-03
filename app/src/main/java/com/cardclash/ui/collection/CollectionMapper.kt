package com.cardclash.ui.collection

import com.cardclash.data.local.model.PlayerProgress
import com.cardclash.domain.model.Card
import com.cardclash.domain.model.Rarity
import com.cardclash.domain.progression.CardCollection
import com.cardclash.domain.progression.ProgressionRules
import com.cardclash.domain.repository.CardCatalog
import com.cardclash.ui.common.ProgressUiMapper

/**
 * Reducer puro (Kotlin/JVM, sin framework) que fusiona la coleccion poseida con
 * el catalogo: nombre real, rareza, coste de mana y copias vs limite del tramo.
 * Testeable con unit tests JVM usando [DefaultCardCatalog] real o fakes.
 */
object CollectionMapper {

    fun toUiState(
        progress: PlayerProgress,
        collection: CardCollection,
        catalog: CardCatalog,
        rules: ProgressionRules = ProgressionRules(),
    ): CollectionUiState {
        val cards = catalog.all()
            .sortedWith(compareBy({ rarityOf(catalog, it).ordinal }, { it.name }))
            .map { card ->
                val rarity = rarityOf(catalog, card)
                val copies = collection.ownedCount(card.id)
                CollectionCardUi(
                    cardId = card.id,
                    name = card.name,
                    rarity = rarity,
                    manaCost = card.cost,
                    copiesOwned = copies,
                    maxCopies = progress.maxCopiesAllowed,
                    canAdd = copies < progress.maxCopiesAllowed,
                    canRemove = copies > 0,
                )
            }
        return CollectionUiState(
            isLoading = false,
            level = progress.level,
            tier = progress.tier,
            xpTotal = progress.xpTotal,
            xpIntoLevel = progress.xpIntoLevel,
            xpNeededForNextLevel = progress.xpNeededForNextLevel,
            xpProgressFraction = ProgressUiMapper.progressFraction(progress, rules),
            maxCopiesAllowed = progress.maxCopiesAllowed,
            cards = cards,
        )
    }

    /**
     * Rareza declarada por el catalogo, o derivada del coste si no la declara
     * (convencion del dominio: coste 5+ = SSR, 4 = SR, resto COMMON).
     */
    private fun rarityOf(catalog: CardCatalog, card: Card): Rarity =
        catalog.rarityOf(card.id) ?: when (card.cost) {
            in 5..Int.MAX_VALUE -> Rarity.SSR
            4 -> Rarity.SR
            else -> Rarity.COMMON
        }
}