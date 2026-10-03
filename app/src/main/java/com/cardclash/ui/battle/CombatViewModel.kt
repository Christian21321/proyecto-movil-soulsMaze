package com.cardclash.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardclash.data.PlayerProgressRepository
import com.cardclash.data.battle.NoOpMatchSessionGateway
import com.cardclash.data.remote.firebase.FirestoreMatchSessionGateway
import com.cardclash.data.LocalMatchResultSink
import com.cardclash.domain.battle.CombatSessionState
import com.cardclash.domain.battle.MatchSessionController
import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.repository.CardCatalog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel (MVVM/UDF) de la pantalla de combate.
 *
 * Envuelve al [MatchSessionController] puro y expone su [CombatSessionState]
 * como [StateFlow] a la UI, delegando cada intención del jugador
 * ([playCard], [endTurn], [beginTurn]) al controlador. La lógica de derivación
 * vive en [CombatReducer] (dominio) y [CombatMapper] (presentación), ambos puros.
 *
 * En el modo por defecto se arranca una partida LOCAL de demo (sin backend) con
 * una puerta de salida NO-OP; la variable [onlineGateway] permite conectar el
 * modo en línea real (Firestore) sin cambiarlo por defecto.
 */
class CombatViewModel(
    private val repository: PlayerProgressRepository,
    private val catalog: CardCatalog = DefaultCardCatalog(),
    private val onlineGateway: FirestoreMatchSessionGateway? = null,
    private val myId: PlayerId = PlayerId(DEFAULT_LOCAL_PLAYER),
) : ViewModel() {

    private val resultSink = LocalMatchResultSink(repository)
    private val sessionGateway = onlineGateway ?: NoOpMatchSessionGateway()

    private val controller = MatchSessionController(
        myId = myId,
        engine = MatchSessionController.productionEngine(catalog),
        catalog = catalog,
        gateway = sessionGateway,
        resultSink = resultSink,
        scope = viewModelScope,
    )

    /** Estado de sesión expuesto a la pantalla (rol + estado + [CombatUiState]). */
    val sessionState: StateFlow<CombatSessionState> = controller.state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        startLocalDemo()
    }

    /** Inicia una partida LOCAL de demostración (modo por defecto de la app). */
    fun startLocalDemo() {
        viewModelScope.launch {
            controller.startLocalDemo(myDeck = buildMyDeck())
        }
    }

    /**
     * Mazo con el que juega el jugador: el mazo activo del Deck Builder o, si no
     * hay ninguno marcado, el último guardado ([PlayerProgressRepository.loadDecks]
     * los ordena activo primero y luego por fecha). Si no hay mazo guardado o ya
     * no es válido, [DeckBuilder.battleDeck] arma uno con la colección.
     */
    suspend fun buildMyDeck(): List<CardId> {
        val saved = repository.loadActiveDeck().getOrNull()
            ?: repository.loadDecks().getOrNull()?.firstOrNull()
        val savedCards = saved?.cardsBySlot?.map { it.cardId }
        return DeckBuilder.battleDeck(savedCards, repository.ownedCollection().ownedCards(), catalog)
    }

    /**
     * Anfitriona una partida EN LÍNEA con el mazo de la colección local.
     * Requiere un [onlineGateway] real; con la puerta NO-OP la sesión pasa a
     * [com.cardclash.domain.battle.SessionStatus.ERROR] (sin backend).
     */
    fun hostMatch() {
        viewModelScope.launch {
            controller.hostMatch(buildMyDeck())
        }
    }

    /**
     * Une al jugador local a una partida EN LÍNEA existente por [code] con el
     * mazo de la colección local. Requiere un [onlineGateway] real.
     */
    fun joinMatch(code: String) {
        viewModelScope.launch {
            controller.joinMatch(code, buildMyDeck())
        }
    }

    // ------------------------------------------------------------------
    // Intenciones UDF delegadas al controlador
    // ------------------------------------------------------------------

    /**
     * Juega la carta [instanceId] apuntando al avatar [targetPlayer].
     * [targetPlayer] es opcional: null = objetivo por defecto según el efecto
     * (p. ej. el rival para los ataques, el propio jugador para las curaciones).
     */
    fun playCard(instanceId: InstanceId, targetPlayer: PlayerId? = null) {
        viewModelScope.launch {
            controller.playCard(instanceId, targetPlayer)
        }
    }

    /** Termina el turno del jugador local. */
    fun endTurn() {
        viewModelScope.launch { controller.endTurn() }
    }

    /** Inicia el turno del jugador local (no-op seguro en demo auto-iniciada). */
    fun beginTurn() {
        viewModelScope.launch { controller.beginTurn() }
    }

    /** Abandona la sesión y cierra todos los listeners. */
    fun leaveMatch() {
        controller.leaveMatch()
        (onlineGateway as? FirestoreMatchSessionGateway)?.let {
            viewModelScope.launch { it.markAbandoned() }
        }
        onlineGateway?.clearSession()
    }

    override fun onCleared() {
        controller.leaveMatch()
        onlineGateway?.clearSession()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_LOCAL_PLAYER = "local-player"
    }
}
