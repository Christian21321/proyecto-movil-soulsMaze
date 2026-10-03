package com.cardclash.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardclash.data.PlayerProgressRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del historial (UDF): carga las [limit] partidas mas recientes con
 * [HistoryMapper] (puro). Recibe el repositorio por constructor para testearlo
 * con fakes.
 */
class HistoryViewModel(
    private val repository: PlayerProgressRepository,
    private val mapper: HistoryMapper = HistoryMapper,
    private val limit: Int = DEFAULT_LIMIT,
) : ViewModel() {

    companion object {
        /** Partidas cargadas por defecto (las mas recientes primero). */
        const val DEFAULT_LIMIT = 50
    }

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refresh()
    }

    /** Recarga el historial de partidas. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            _uiState.value = try {
                mapper.toUiState(repository.matchHistory(limit))
            } catch (e: Exception) {
                _messages.tryEmit("No se pudo cargar el historial")
                _uiState.value.copy(isLoading = false)
            }
        }
    }
}