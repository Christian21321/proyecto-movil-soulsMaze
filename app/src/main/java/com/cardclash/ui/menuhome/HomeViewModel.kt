package com.cardclash.ui.menuhome

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
 * ViewModel del menu principal (UDF): expone [uiState] (StateFlow inmutable) y
 * [messages] (SharedFlow one-shot). Recibe el repositorio por constructor para
 * poder testearlo con fakes; la transformacion vive en [HomeMapper] (pura).
 */
class HomeViewModel(
    private val repository: PlayerProgressRepository,
    private val mapper: HomeMapper = HomeMapper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refresh()
    }

    /** Recarga el resumen de progreso y de coleccion. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            _uiState.value = try {
                val progress = repository.currentProgress()
                val collection = repository.ownedCollection()
                mapper.toUiState(progress, collection)
            } catch (e: Exception) {
                _messages.tryEmit("No se pudo cargar el progreso del jugador")
                _uiState.value.copy(isLoading = false)
            }
        }
    }
}