package com.cardclash.ui.deckbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardclash.data.PlayerProgressRepository
import com.cardclash.data.local.entity.PlayerProfileEntity
import com.cardclash.data.local.model.Deck
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.progression.CardCollection
import com.cardclash.domain.repository.CardCatalog
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CardTypeFilter
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.DeckUiModel
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.Editing
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.FilterState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del constructor de mazos (UDF). Carga el mazo [deckId] (o uno
 * vacío si es null), aplica las intenciones del usuario con
 * [DeckBuilderMapper] y guarda vía [PlayerProgressRepository]. El mazo
 * guardado pasa a ser el activo, que es con el que se entra al combate.
 * Los mensajes one-shot salen por [messages].
 */
class DeckBuilderViewModel(
    private val repository: PlayerProgressRepository,
    private val catalog: CardCatalog,
    private val deckId: String?,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeckBuilderUiState>(DeckBuilderUiState.Loading)
    val uiState: StateFlow<DeckBuilderUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var collection: CardCollection? = null
    private var loadedDeck: Deck? = null

    init {
        load()
    }

    /** Carga la colección y el mazo a editar. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = DeckBuilderUiState.Loading
            _uiState.value = try {
                val owned = repository.ownedCollection()
                collection = owned
                val deck = deckId?.let { id ->
                    repository.loadDecks().getOrThrow().firstOrNull { it.id.value == id }
                }
                loadedDeck = deck
                val deckUi = deck?.let { DeckBuilderMapper.toUiModel(it, catalog) }
                    ?: DeckBuilderMapper.emptyDeck()
                buildEditing(deckUi, FilterState())
            } catch (e: Exception) {
                DeckBuilderUiState.Error("No se pudo cargar el mazo")
            }
        }
    }

    fun onNameChange(name: String) = updateDeck { it.copy(name = name, isModified = true) }

    fun onAddCard(cardId: CardId) {
        val card = catalog.findById(cardId) ?: return
        updateDeck { DeckBuilderMapper.addCard(it, card, catalog) }
    }

    fun onRemoveSlot(slotIndex: Int) = updateDeck { DeckBuilderMapper.removeCard(it, slotIndex) }

    fun onToggleType(type: CardTypeFilter) = updateFilter { filter ->
        val types = if (type in filter.types) filter.types - type else filter.types + type
        filter.copy(types = types)
    }

    fun onOnlyOwnedChange(onlyOwned: Boolean) = updateFilter { it.copy(onlyOwned = onlyOwned) }

    /** Guarda el mazo si es válido y lo marca como activo. */
    fun onSave() {
        val state = _uiState.value as? Editing ?: return
        if (state.isSaving) return
        if (!state.validation.isValid) {
            _messages.tryEmit(state.validation.errors.first())
            return
        }
        _uiState.value = state.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            val now = clock()
            val result = runCatching {
                DeckBuilderMapper.toDomain(
                    deckUi = state.deck,
                    playerId = PlayerId(PlayerProfileEntity.DEFAULT_PLAYER_ID),
                    now = now,
                    createdAt = loadedDeck?.createdAt ?: now,
                )
            }.mapCatching { deck -> repository.saveDeck(deck).getOrThrow() }

            result.onSuccess { saved ->
                // El último mazo guardado pasa a ser el activo: es con el que
                // se entra al combate.
                repository.setActiveDeck(saved.id)
                _messages.tryEmit("Mazo guardado")
                _uiState.value = DeckBuilderUiState.Saved
            }.onFailure { error ->
                val message = error.message ?: "No se pudo guardar el mazo"
                _messages.tryEmit(message)
                val current = _uiState.value as? Editing
                if (current != null) _uiState.value = current.copy(isSaving = false, saveError = message)
            }
        }
    }

    /**
     * Intención de salir. Devuelve true si se puede salir ya; si hay cambios
     * sin guardar, pide confirmación y devuelve false.
     */
    fun onBackPress(): Boolean {
        val state = _uiState.value as? Editing ?: return true
        if (!state.deck.isModified) return true
        _uiState.value = state.copy(confirmDiscard = true)
        return false
    }

    fun onDismissDiscard() {
        val state = _uiState.value as? Editing ?: return
        _uiState.value = state.copy(confirmDiscard = false)
    }

    private fun updateDeck(transform: (DeckUiModel) -> DeckUiModel) {
        val state = _uiState.value as? Editing ?: return
        _uiState.value = buildEditing(transform(state.deck), state.filter)
    }

    private fun updateFilter(transform: (FilterState) -> FilterState) {
        val state = _uiState.value as? Editing ?: return
        _uiState.value = buildEditing(state.deck, transform(state.filter))
    }

    private fun buildEditing(deck: DeckUiModel, filter: FilterState): Editing {
        val owned = collection ?: CardCollection()
        val validation = DeckBuilderMapper.validate(deck, owned, catalog)
        val deckWithErrors = DeckBuilderMapper.withSlotErrors(deck, validation)
        val cards = DeckBuilderMapper.collectionCards(deckWithErrors, owned, catalog)
        return Editing(
            deck = deckWithErrors,
            collection = DeckBuilderMapper.applyFilter(cards, filter),
            filter = filter,
            validation = validation,
            manaCurve = DeckBuilderMapper.manaCurve(deckWithErrors),
        )
    }
}
