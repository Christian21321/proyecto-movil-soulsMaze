package com.cardclash.ui.deckbuilder

import com.cardclash.data.local.model.Deck
import com.cardclash.data.local.model.DeckCard
import com.cardclash.data.local.model.DeckId
import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardEffect
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.Rarity
import com.cardclash.domain.progression.CardCollection
import com.cardclash.domain.repository.CardCatalog
import com.cardclash.domain.service.DeckRuleService
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CardTypeFilter
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CollectionCardUi
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.DeckSlotUi
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.DeckUiModel
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.FilterState
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ManaCurveData
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ValidationResult
import java.util.UUID

/**
 * Lógica PURA del constructor de mazos: conversión dominio <-> UI, colección,
 * filtros, validación y curva de mana. No depende de Android, así que se
 * prueba con tests JVM.
 */
object DeckBuilderMapper {

    /** Mazo nuevo: 8 huecos normales vacíos + 1 hueco pasivo vacío. */
    fun emptyDeck(name: String = "Mi mazo"): DeckUiModel =
        DeckUiModel(id = null, name = name, slots = emptySlots())

    /**
     * Convierte un mazo guardado a la UI. Las cartas normales ocupan los huecos
     * 0..7 en orden de slot y la pasiva el hueco 8, aunque se hubieran guardado
     * en otra posición.
     */
    fun toUiModel(deck: Deck, catalog: CardCatalog): DeckUiModel {
        val ordered = deck.cardsBySlot.mapNotNull { catalog.findById(it.cardId) }
        val normals = ordered.filterNot { it.isPassive }.take(DeckBuilderUiState.NON_PASSIVE_SLOTS)
        val passive = ordered.firstOrNull { it.isPassive }
        val slots = emptySlots().map { slot ->
            val card = if (slot.isPassiveSlot) passive else normals.getOrNull(slot.index)
            if (card == null) slot else fill(slot, card, catalog)
        }
        return DeckUiModel(id = deck.id.value, name = deck.name, slots = slots)
    }

    /**
     * Convierte el mazo de la UI al modelo que guarda el repositorio. Si el mazo
     * es nuevo ([DeckUiModel.id] null) se genera un UUID; [createdAt] conserva la
     * fecha original al editar uno existente.
     */
    fun toDomain(deckUi: DeckUiModel, playerId: PlayerId, now: Long, createdAt: Long = now): Deck = Deck(
        id = DeckId(deckUi.id ?: UUID.randomUUID().toString()),
        name = deckUi.name.trim(),
        isActive = false,
        playerId = playerId,
        createdAt = createdAt,
        updatedAt = now,
        cards = deckUi.slots.mapNotNull { slot -> slot.cardId?.let { DeckCard(slot.index, it) } },
    )

    /** Coloca [card] en el primer hueco libre de su tipo; sin hueco, devuelve el mazo igual. */
    fun addCard(deckUi: DeckUiModel, card: Card, catalog: CardCatalog): DeckUiModel {
        if (deckUi.cardIds.contains(card.id)) return deckUi
        val target = deckUi.slots.firstOrNull { it.isEmpty && it.isPassiveSlot == card.isPassive }
            ?: return deckUi
        val slots = deckUi.slots.map { if (it.index == target.index) fill(it, card, catalog) else it }
        return deckUi.copy(slots = slots, isModified = true)
    }

    /** Vacía el hueco [slotIndex]. */
    fun removeCard(deckUi: DeckUiModel, slotIndex: Int): DeckUiModel {
        val slots = deckUi.slots.map {
            if (it.index == slotIndex) DeckSlotUi(index = it.index, isPassiveSlot = it.isPassiveSlot) else it
        }
        return deckUi.copy(slots = slots, isModified = true)
    }

    /** Todas las cartas del catálogo con su estado respecto a la colección y al mazo. */
    fun collectionCards(
        deckUi: DeckUiModel,
        collection: CardCollection,
        catalog: CardCatalog,
    ): List<CollectionCardUi> {
        val inDeck = deckUi.cardIds.toSet()
        val freeNormal = deckUi.slots.any { it.isEmpty && !it.isPassiveSlot }
        val freePassive = deckUi.slots.any { it.isEmpty && it.isPassiveSlot }
        return catalog.all()
            .sortedWith(compareBy<Card>({ it.cost }, { it.name }))
            .map { card ->
                val owned = collection.ownedCount(card.id)
                val hasRoom = if (card.isPassive) freePassive else freeNormal
                CollectionCardUi(
                    cardId = card.id,
                    name = card.name,
                    rarity = catalog.rarityOf(card.id) ?: Rarity.COMMON,
                    manaCost = card.cost,
                    type = typeOf(card),
                    copiesOwned = owned,
                    isPassive = card.isPassive,
                    effectDescription = describeEffect(card),
                    inDeck = card.id in inDeck,
                    canAdd = owned > 0 && card.id !in inDeck && hasRoom,
                )
            }
    }

    /** Aplica [filter] a la colección completa. */
    fun applyFilter(cards: List<CollectionCardUi>, filter: FilterState): List<CollectionCardUi> =
        cards.filter { card ->
            (filter.types.isEmpty() || card.type in filter.types) &&
                (!filter.onlyOwned || card.copiesOwned > 0)
        }

    /**
     * Valida el mazo con [DeckRuleService] y además exige al menos una carta y
     * que todas las cartas estén en la colección.
     */
    fun validate(deckUi: DeckUiModel, collection: CardCollection, catalog: CardCatalog): ValidationResult {
        val errors = mutableListOf<String>()
        if (deckUi.name.isBlank()) errors += "El mazo necesita un nombre."
        if (deckUi.cardIds.isEmpty()) errors += "Añade al menos una carta."
        errors += DeckRuleService(catalog).validate(deckUi.cardIds).errors
        val notOwned = deckUi.slots.filter { slot ->
            slot.cardId != null && collection.ownedCount(slot.cardId) == 0
        }
        notOwned.forEach { errors += "No posees la carta ${it.name ?: it.cardId?.value}." }
        return ValidationResult(errors = errors, slotsWithErrors = notOwned.map { it.index }.toSet())
    }

    /** Marca en los huecos los errores de [validation]. */
    fun withSlotErrors(deckUi: DeckUiModel, validation: ValidationResult): DeckUiModel =
        deckUi.copy(slots = deckUi.slots.map { it.copy(hasError = it.index in validation.slotsWithErrors) })

    /** Curva de mana de las cartas normales del mazo. */
    fun manaCurve(deckUi: DeckUiModel): ManaCurveData = ManaCurveData(
        byCost = deckUi.slots
            .filter { !it.isPassiveSlot && it.manaCost != null }
            .groupingBy { it.manaCost!! }
            .eachCount(),
    )

    fun typeOf(card: Card): CardTypeFilter = when {
        card.isPassive -> CardTypeFilter.PASSIVE
        else -> when (card.effect) {
            is CardEffect.Attack -> CardTypeFilter.ATTACK
            is CardEffect.Heal -> CardTypeFilter.HEAL
            is CardEffect.Draw -> CardTypeFilter.DRAW
            is CardEffect.ApplyStatus -> CardTypeFilter.STATUS
            is CardEffect.PassiveBuff -> CardTypeFilter.PASSIVE
            is CardEffect.None -> CardTypeFilter.ATTACK
        }
    }

    fun describeEffect(card: Card): String {
        if (card.isPassive) {
            val stat = card.passiveStat?.name ?: ""
            return "Pasiva: $stat ${if (card.passiveAmount > 0) "+" else ""}${card.passiveAmount}"
        }
        return when (val effect = card.effect) {
            is CardEffect.Attack -> "Daño ${effect.amount}"
            is CardEffect.Heal -> "Cura ${effect.amount}"
            is CardEffect.Draw -> "Roba ${effect.count}"
            is CardEffect.ApplyStatus -> "${effect.status.name} (${effect.durationTurns} turnos)"
            is CardEffect.PassiveBuff -> "${effect.stat.name} +${effect.amount}"
            is CardEffect.None -> "Sin efecto"
        }
    }

    private fun emptySlots(): List<DeckSlotUi> =
        (0..DeckBuilderUiState.PASSIVE_SLOT_INDEX).map { index ->
            DeckSlotUi(index = index, isPassiveSlot = index == DeckBuilderUiState.PASSIVE_SLOT_INDEX)
        }

    private fun fill(slot: DeckSlotUi, card: Card, catalog: CardCatalog): DeckSlotUi = slot.copy(
        cardId = card.id,
        name = card.name,
        rarity = catalog.rarityOf(card.id) ?: Rarity.COMMON,
        manaCost = card.cost,
        hasError = false,
    )

    /** Atajo para tests y previews: mazo con las cartas [ids] colocadas en orden. */
    fun deckWith(ids: List<CardId>, catalog: CardCatalog, name: String = "Mi mazo"): DeckUiModel =
        ids.mapNotNull { catalog.findById(it) }
            .fold(emptyDeck(name)) { deck, card -> addCard(deck, card, catalog) }
            .copy(isModified = false)
}
