package com.cardclash.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardclash.data.PlayerProgressRepository
import com.cardclash.data.local.logic.CardCollectionOps.AddCopyOutcome
import com.cardclash.data.local.logic.CardCollectionOps.RemoveCopyOutcome
import com.cardclash.domain.model.CardId
import com.cardclash.domain.repository.CardCatalog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de coleccion + progresion (UDF). Carga la coleccion fusionada con
 * el catalogo y despacha anadir/eliminar copias delegando la validacion de
 * limites al dominio (el repositorio usa [ProgressionRules].maxCopiesAllowed).
 * Los mensajes one-shot salen por [messages].
 */
class CollectionViewModel(
    private val repository: PlayerProgressRepository,
    private val catalog: CardCatalog,
    private val mapper: CollectionMapper = CollectionMapper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refresh()
    }

    /** Recarga progresion + coleccion fusionada con el catalogo. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            _uiState.value = try {
                val progress = repository.currentProgress()
                mapper.toUiState(progress, repository.ownedCollection(), catalog)
            } catch (e: Exception) {
                _messages.tryEmit("No se pudo cargar la coleccion")
                _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** Intencion UDF: anadir una copia de [cardId] (el dominio valida el limite). */
    fun addCopy(cardId: CardId) {
        viewModelScope.launch {
            when (val outcome = repository.addCardCopy(cardId)) {
                is AddCopyOutcome.Added -> {
                    _messages.tryEmit("Copia anadida (${outcome.newCount})")
                    refresh()
                }
                is AddCopyOutcome.LimitReached -> {
                    _messages.tryEmit("Limite de ${outcome.maxAllowed} copias alcanzado a este nivel")
                }
                is AddCopyOutcome.UnknownCard -> {
                    _messages.tryEmit("Carta desconocida en el catalogo")
                }
            }
        }
    }

    /** Intencion UDF: eliminar una copia de [cardId]. */
    fun removeCopy(cardId: CardId) {
        viewModelScope.launch {
            when (val outcome = repository.removeCardCopy(cardId)) {
                is RemoveCopyOutcome.Removed -> {
                    _messages.tryEmit(
                        if (outcome.newCount == 0) "Carta retirada de la coleccion" else "Copia eliminada (${outcome.newCount})",
                    )
                    refresh()
                }
                is RemoveCopyOutcome.NotOwned -> {
                    _messages.tryEmit("No posees ninguna copia de esta carta")
                }
            }
        }
    }
}