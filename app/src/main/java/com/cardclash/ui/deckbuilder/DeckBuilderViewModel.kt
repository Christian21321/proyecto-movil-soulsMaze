package com.cardclash.ui.deckbuilder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardclash.data.PlayerProgressRepository
import com.cardclash.data.local.model.Deck
import com.cardclash.data.local.model.PlayerProgress
import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.Rarity
import com.cardclash.domain.progression.CardCollection
import com.cardclash.domain.progression.ProgressionRules
import com.cardclash.domain.repository.CardCatalog
import com.cardclash.domain.service.DeckRuleService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.runCatching

// Tipos de DeckBuilderUiState (mismo paquete, pero requieren import explícito por estar en sealed interface)
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.Loading
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.Editing
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.Saving
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.Saved
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.Error
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ConfirmDiscardChanges
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.DeckUiModel
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.FilterState
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CollectionCardUi
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ValidationResult
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ValidationError
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ValidationWarning
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.SlotValidationStatus
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.DeckSlotUi
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ManaCurveData
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CardTypeFilter
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CopyFilter

class DeckBuilderViewModel(
    private val deckRepository: PlayerProgressRepository,
    private val catalog: CardCatalog,
    private val progressRepository: PlayerProgressRepository,
    private val savedStateHandle: SavedStateHandle,
    private val rules: ProgressionRules = ProgressionRules(),
) : ViewModel() {

    private val deckRuleService = DeckRuleService(catalog)
    private val _uiState = MutableStateFlow<DeckBuilderUiState>(DeckBuilderUiState.Loading())
    val uiState: StateFlow<DeckBuilderUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.UNLIMITED)
    val messages = _messages.receiveAsFlow()

    private val deckId: String? = savedStateHandle.get<String>("deckId")
    private val isNewDeck: Boolean = savedStateHandle.get<Boolean>("isNewDeck") ?: (deckId == null)

    init {
        loadInitial()
    }

    fun onSlotClick(slotIndex: Int) {
        _uiState.value = _uiState.value.let { current ->
            if (current is DeckBuilderUiState.Editing) {
                val newFilter = if (current.deck.slots[slotIndex].cardId == null) {
                    current.filter.copy(onlyCompatible = true)
                } else {
                    current.filter
                }
                current.copy(
                    filter = newFilter,
                    isCollectionExpanded = true
                )
            } else current
        }
    }

    fun onCollectionCardAdd(card: DeckBuilderUiState.CollectionCardUi) {
        _uiState.value = _uiState.value.let { current ->
            if (current is DeckBuilderUiState.Editing) {
                val bestSlot = findBestSlotFor(card, current.deck)
                if (bestSlot == -1) return@let current
                val newSlots = current.deck.slots.mapIndexed { index, slot ->
                    if (index == bestSlot) {
                        slot.copy(
                            cardId = card.cardId,
                            name = card.name,
                            rarity = card.rarity,
                            manaCost = card.manaCost,
                            isPassive = card.isPassive,
                            state = DeckBuilderUiState.SlotValidationStatus.Filled
                        )
                    } else slot
                }
                val newDeck = current.deck.copy(
                    slots = newSlots,
                    nonPassiveCount = newSlots.count { !it.isPassive && it.cardId != null },
                    passiveCount = newSlots.count { it.isPassive && it.cardId != null },
                    totalCopiesInDeck = newSlots.count { it.cardId != null },
                    isModified = true
                )
                val newValidation = recalculateValidation(newDeck, current.collection, currentProgress())
                val newManaCurve = recalculateManaCurve(newDeck)
                current.copy(
                    deck = newDeck,
                    validation = newValidation,
                    manaCurve = newManaCurve,
                    isCollectionExpanded = false
                )
            } else current
        }
    }

    fun onSlotCardRemove(slotIndex: Int) {
        _uiState.value = _uiState.value.let { current ->
            if (current is DeckBuilderUiState.Editing) {
                val newSlots = current.deck.slots.mapIndexed { index, slot ->
                    if (index == slotIndex) {
                        slot.copy(
                            cardId = null,
                            name = null,
                            rarity = null,
                            manaCost = null,
                            isPassive = false,
                            state = DeckBuilderUiState.SlotValidationStatus.Empty
                        )
                    } else slot
                }
                val newDeck = current.deck.copy(
                    slots = newSlots,
                    nonPassiveCount = newSlots.count { !it.isPassive && it.cardId != null },
                    passiveCount = newSlots.count { it.isPassive && it.cardId != null },
                    totalCopiesInDeck = newSlots.count { it.cardId != null },
                    isModified = true
                )
                val newValidation = recalculateValidation(newDeck, current.collection, currentProgress())
                val newManaCurve = recalculateManaCurve(newDeck)
                current.copy(
                    deck = newDeck,
                    validation = newValidation,
                    manaCurve = newManaCurve
                )
            } else current
        }
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.let { current ->
            if (current is DeckBuilderUiState.Editing) {
                current.copy(deck = current.deck.copy(name = name, isModified = true))
            } else current
        }
    }

    fun onFilterChange(filter: DeckBuilderUiState.FilterState) {
        _uiState.value = _uiState.value.let { current ->
            if (current is DeckBuilderUiState.Editing) {
                val newCollection = buildFilteredCollection(current.deck, filter)
                current.copy(filter = filter, collection = newCollection)
            } else current
        }
    }

    fun onCollectionExpandChanged(expanded: Boolean) {
        _uiState.value = _uiState.value.let { current ->
            if (current is DeckBuilderUiState.Editing) {
                current.copy(isCollectionExpanded = expanded)
            } else current
        }
    }

    fun onSaveClick() {
        _uiState.value = _uiState.value.let { current ->
            if (current is DeckBuilderUiState.Editing) {
                current.copy(isSaving = true, saveError = null)
            } else current
        }

        viewModelScope.launch {
            val current = _uiState.value
            if (current is DeckBuilderUiState.Editing) {
                if (!current.validation.isValid) {
                    _uiState.value = current.copy(isSaving = false, saveError = "El mazo tiene errores de validación")
                    return@launch
                }
                val domainDeck = DeckBuilderMapper.toDomain(current.deck)
                val result = deckRepository.saveDeck(domainDeck)
                result.onSuccess { saved ->
                    // El último mazo guardado pasa a ser el activo: es con el
                    // que se entra al combate.
                    deckRepository.setActiveDeck(saved.id)
                    _messages.trySend("Guardado")
                    _uiState.value = DeckBuilderUiState.Saved()
                }.onFailure { error ->
                    _uiState.value = current.copy(isSaving = false, saveError = error.message ?: "Error al guardar")
                }
            }
        }
    }

    fun onDiscardChanges() {
        _uiState.value = _uiState.value.let { current ->
            if (current is DeckBuilderUiState.Editing) {
                DeckBuilderUiState.ConfirmDiscardChanges(
                    onConfirm = { loadInitial() },
                    onCancel = { _uiState.value = current }
                )
            } else current
        }
    }

    fun onBackPress(): Boolean {
        val current = _uiState.value
        if (current is DeckBuilderUiState.Editing && current.deck.isModified) {
            onDiscardChanges()
            return true
        }
        return false
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.value = DeckBuilderUiState.Loading(isNewDeck)
            runCatching {
                val progress = progressRepository.currentProgress()
                val collection = progressRepository.ownedCollection()
                val deck: Deck? = if (deckId != null) {
                    progressRepository.loadDecks().getOrThrow().find { it.id.value == deckId }
                } else null
                val deckUi = if (deck != null) {
                    DeckBuilderMapper.toUiModel(deck, collection, catalog, progress.level, rules)
                } else {
                    createNewDeckUiModel(progress)
                }
                val collectionUi = buildCollectionUi(collection, progress, deckUi)
                val validation = recalculateValidation(deckUi, collectionUi, progress)
                val manaCurve = recalculateManaCurve(deckUi)
                _uiState.value = DeckBuilderUiState.Editing(
                    deck = deckUi,
                    collection = collectionUi,
                    filter = DeckBuilderUiState.FilterState(),
                    validation = validation,
                    manaCurve = manaCurve
                )
            }.onFailure { error ->
                _uiState.value = DeckBuilderUiState.Error(error.message ?: "Error al cargar")
            }
        }
    }

    private fun createNewDeckUiModel(progress: PlayerProgress): DeckBuilderUiState.DeckUiModel {
        val emptySlots = (0..8).map { index ->
            DeckBuilderUiState.DeckSlotUi(index = index, state = DeckBuilderUiState.SlotValidationStatus.Empty)
        }
        return DeckBuilderUiState.DeckUiModel(
            id = null,
            name = "Mi Mazo",
            slots = emptySlots,
            nonPassiveCount = 0,
            passiveCount = 0,
            totalCopiesInDeck = 0,
            isModified = false
        )
    }

    private fun buildCollectionUi(
        collection: CardCollection,
        progress: PlayerProgress,
        deckUi: DeckBuilderUiState.DeckUiModel
    ): List<DeckBuilderUiState.CollectionCardUi> {
        val maxCopiesAtLevel = rules.maxCopiesAllowed(progress.level)
        val currentCardIds = deckUi.slots.mapNotNull { it.cardId }.toSet()
        val passiveCountInDeck = deckUi.slots.count { it.isPassive && it.cardId != null }
        val nonPassiveCountInDeck = deckUi.slots.count { !it.isPassive && it.cardId != null }

        return catalog.all()
            .sortedWith(compareBy({ catalog.rarityOf(it)?.ordinal ?: Rarity.COMMON.ordinal }, { it.name }))
            .map { card ->
                val ownedCopies = collection.ownedCount(card.id)
                val canAdd = rules.canAddCopy(card, ownedCopies, progress.level)

                val duplicateInDeck = currentCardIds.contains(card.id)
                val wouldExceedPassive = card.isPassive && passiveCountInDeck >= 1
                val wouldExceedNonPassive = !card.isPassive && nonPassiveCountInDeck >= 8
                val isCompatible = canAdd && !duplicateInDeck && !wouldExceedPassive && !wouldExceedNonPassive

                DeckBuilderMapper.toCollectionCardUi(
                    card = card,
                    ownedCopies = ownedCopies,
                    maxCopiesAtLevel = maxCopiesAtLevel,
                    isFavorite = false
                ).copy(
                    canAdd = canAdd && isCompatible,
                    isCompatible = isCompatible
                )
            }
    }

    private fun buildFilteredCollection(
        deckUi: DeckBuilderUiState.DeckUiModel,
        filter: DeckBuilderUiState.FilterState
    ): List<DeckBuilderUiState.CollectionCardUi> {
        val current = _uiState.value
        if (current !is DeckBuilderUiState.Editing) return emptyList()

        var filtered = current.collection

        if (filter.types.isNotEmpty() && !filter.types.contains(DeckBuilderUiState.CardTypeFilter.ALL)) {
            filtered = filtered.filter { card ->
                val cardDomain = catalog.findById(card.cardId)
                if (cardDomain == null) false
                else {
                    val type = getTypeFilter(cardDomain)
                    filter.types.contains(type)
                }
            }
        }
        if (filter.rarities.isNotEmpty()) {
            filtered = filtered.filter { filter.rarities.contains(it.rarity) }
        }
        if (filter.manaCosts.isNotEmpty()) {
            filtered = filtered.filter { filter.manaCosts.contains(it.manaCost) }
        }
        if (filter.onlyOwned) {
            filtered = filtered.filter { it.copiesOwned > 0 }
        }
        if (filter.onlyCompatible) {
            filtered = filtered.filter { it.isCompatible }
        }
        when (filter.copyFilter) {
            DeckBuilderUiState.CopyFilter.Missing -> filtered = filtered.filter { it.copiesOwned < it.maxCopies }
            DeckBuilderUiState.CopyFilter.Owned -> filtered = filtered.filter { it.copiesOwned > 0 }
            DeckBuilderUiState.CopyFilter.All -> {}
        }
        return filtered
    }

    private fun recalculateValidation(
        deckUi: DeckBuilderUiState.DeckUiModel,
        collectionUi: List<DeckBuilderUiState.CollectionCardUi>,
        progress: PlayerProgress
    ): DeckBuilderUiState.ValidationResult {
        val cardIds = deckUi.slots.mapNotNull { it.cardId }
        val domainValidation = deckRuleService.validate(cardIds)

        val errors = mutableListOf<DeckBuilderUiState.ValidationError>()
        val warnings = mutableListOf<DeckBuilderUiState.ValidationWarning>()
        val slotIssues = mutableMapOf<Int, MutableList<DeckBuilderUiState.ValidationError>>()

        domainValidation.errors.forEach { errorMsg ->
            errors.add(DeckBuilderUiState.ValidationError("DECK_RULE", errorMsg))
        }

        val copyCounts: Map<CardId, Int> = cardIds.groupingBy { it }.eachCount()
        val maxCopies = rules.maxCopiesAllowed(progress.level)

        deckUi.slots.forEachIndexed { index: Int, slot: DeckBuilderUiState.DeckSlotUi ->
            slot.cardId?.let { cardId: CardId ->
                val slotErrors = mutableListOf<DeckBuilderUiState.ValidationError>()
                val ownedCopies = collectionUi.firstOrNull { it.cardId == cardId }?.copiesOwned ?: 0
                val inDeckCount = copyCounts[cardId] ?: 0

                if (inDeckCount > 1) {
                    val err = DeckBuilderUiState.ValidationError("DUPLICATE", "Carta duplicada en el mazo", index)
                    slotErrors.add(err)
                    errors.add(err)
                }
                if (ownedCopies == 0) {
                    val err = DeckBuilderUiState.ValidationError("NOT_OWNED", "No posees esta carta", index)
                    slotErrors.add(err)
                    errors.add(err)
                }
                if (inDeckCount > maxCopies) {
                    val err = DeckBuilderUiState.ValidationError("COPY_LIMIT", "Supera el límite de copias ($maxCopies)", index)
                    slotErrors.add(err)
                    errors.add(err)
                }
                if (slot.isPassive && deckUi.passiveCount > 1) {
                    val warn = DeckBuilderUiState.ValidationWarning("TOO_MANY_PASSIVE", "Máximo 1 carta pasiva", index)
                    warnings.add(warn)
                }
                if (!slot.isPassive && deckUi.nonPassiveCount > 8) {
                    val err = DeckBuilderUiState.ValidationError("TOO_MANY_NON_PASSIVE", "Máximo 8 cartas no pasivas", index)
                    slotErrors.add(err)
                    errors.add(err)
                }

                if (slotErrors.isNotEmpty()) {
                    slotIssues[index] = slotErrors
                }
            }
        }

        return DeckBuilderUiState.ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            slotIssues = slotIssues.mapValues { (_, value) -> value.toList() }
        )
    }

    private suspend fun currentProgress(): PlayerProgress = progressRepository.currentProgress()

    private fun findBestSlotFor(card: DeckBuilderUiState.CollectionCardUi, deckUi: DeckBuilderUiState.DeckUiModel): Int {
        if (card.isPassive) {
            return deckUi.slots.indexOfFirst { it.isPassive && it.cardId == null }
        } else {
            return deckUi.slots.indexOfFirst { !it.isPassive && it.cardId == null }
        }
    }

    private fun recalculateManaCurve(deckUi: DeckBuilderUiState.DeckUiModel): DeckBuilderUiState.ManaCurveData {
        return DeckBuilderMapper.toManaCurveData(deckUi, catalog)
    }

    private fun getTypeFilter(card: Card): DeckBuilderUiState.CardTypeFilter = when (card.effect) {
        is com.cardclash.domain.model.CardEffect.Attack -> DeckBuilderUiState.CardTypeFilter.ATTACK
        is com.cardclash.domain.model.CardEffect.Heal -> DeckBuilderUiState.CardTypeFilter.HEAL
        is com.cardclash.domain.model.CardEffect.Draw -> DeckBuilderUiState.CardTypeFilter.DRAW
        is com.cardclash.domain.model.CardEffect.ApplyStatus -> DeckBuilderUiState.CardTypeFilter.STATUS
        is com.cardclash.domain.model.CardEffect.PassiveBuff -> DeckBuilderUiState.CardTypeFilter.PASSIVE
        else -> DeckBuilderUiState.CardTypeFilter.ALL
    }
}