package com.cardclash.domain.battle

import com.cardclash.data.remote.dto.ClientProjectionWire
import com.cardclash.data.remote.sync.ActionPayload
import com.cardclash.data.remote.sync.ActionType
import com.cardclash.data.remote.sync.ClientSync
import com.cardclash.data.remote.sync.HostSyncEngine
import com.cardclash.data.remote.sync.HostSyncState
import com.cardclash.data.remote.sync.MatchAction
import com.cardclash.domain.engine.CombatEngine
import com.cardclash.domain.engine.DefaultCardCatalog
import com.cardclash.domain.engine.SeededDiceRoller
import com.cardclash.domain.model.CardEffect
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchResult
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.repository.CardCatalog
import com.cardclash.domain.service.MatchFactory
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Cerebro PURo e inmutable de la sesión de combate (UDF).
 *
 * Orquesta el [CombatEngine] + la capa de sync local ([HostSyncEngine]) y los
 * puertos de salida ([MatchSessionGateway], [MatchResultSink]) para mantener el
 * [CombatUiState] vigente. NO importa `android.*` ni `com.google.firebase.*`:
 * toda la infraestructura real queda detrás de los puertos, por lo que es
 * 100% testeable en JVM con un gateway FAKE.
 *
 * # Descomposición funcional (intención -> efecto)
 *
 * - [startLocalDemo]: modo LOCAL/SOLO. Ambos lados se simulan en este proceso
 *   con un único [CombatEngine] + [HostSyncEngine]. El jugador local actúa vía
 *   [playCard]/[endTurn]/[beginTurn]; el oponente ("BOT") juega su turno de forma
 *   automática ([playBotTurn]). Al terminar se produce un [MatchResult] y se
 *   registra en [MatchResultSink] (5 XP victoria / 2 XP derrota).
 *
 * - [hostMatch]: modo anfitrión EN LÍNEA. Crea la partida vía [MatchSessionGateway]
 *   y se suscribe al estado y a las acciones remotas. Sus propias intenciones se
 *   ENCOLAN por el gateway y se procesan al retornar por [listenHostActions]
 *   ([onHostAction]), actualizando el estado y publicando el snapshot. Los
 *   rechazos/duplicados/fuera de orden se registran en el [CombatSessionState.rejectionLog].
 *
 * - [joinMatch]: modo cliente EN LÍNEA. Se une por código y consume proyecciones
 *   filtradas ([ClientProjectionWire]) que NUNCA revelan la mano del rival; sus
 *   acciones se encolan con una secuencia monótona propia ([ClientSync]).
 *
 * # Flujo del modo local demo (el que usará el frontend para la demo visible)
 *
 * 1. [startLocalDemo] crea la partida ([MatchFactory.newMatch]), configura un
 *    [HostSyncEngine] (host = yo, client = BOT) y AUTO-INICIA el turno de "yo"
 *    (BeginTurn: mana lleno + robo).
 * 2. La UI juega cartas jugables ([playCard]) y termina el turno ([endTurn]).
 * 3. Al terminar mi turno, si no hay ganador, el controlador invoca [playBotTurn]:
 *    el BOT inicia su turno, juega cartas razonables (prioriza dañar mi
 *    avatar) y termina su turno. Al volver a mi turno, se AUTO-INICIA de nuevo.
 * 4. Cuando una acción produce [MatchResult], el controlador pasa a FINISHED,
 *    publica la vista final y registra el resultado en [MatchResultSink].
 *
 * La secuencia de acciones es la que espera [HostSyncEngine] (monótona y
 * autocorregida: se lee de [HostSyncState.nextExpectedActionSeq]).
 */
class MatchSessionController(
    private val myId: PlayerId,
    private val engine: CombatEngine,
    private val catalog: CardCatalog = DefaultCardCatalog(),
    private val gateway: MatchSessionGateway,
    private val resultSink: MatchResultSink,
    private val scope: CoroutineScope,
    private val maxLogLines: Int = 8,
) {
    /** Identidad del oponente simulado en el modo local demo. */
    private val botPlayer = PlayerId("BOT")

    private val factory: MatchFactory = MatchFactory(catalog, engine)

    private val _state = MutableStateFlow(CombatSessionState(status = SessionStatus.IDLE))

    /** Flujo inmutable que la UI consume para renderizar la pantalla de combate. */
    val state: StateFlow<CombatSessionState> = _state.asStateFlow()

    // --- Estado interno (solo mutado por métodos privados UDF) ---

    private val activeListeners = mutableListOf<Closeable>()

    // Local demo
    private var localSync: HostSyncEngine? = null
    private var localState: HostSyncState? = null
    private var myTurnBegun: Boolean = false

    // Host online
    private var hostSync: HostSyncEngine? = null
    private var hostState: HostSyncState? = null
    private var hostMatchId: MatchId? = null

    // Client online
    private var clientSync: ClientSync? = null
    private var clientMatchId: MatchId? = null
    private var myCardOf: Map<InstanceId, CardId> = emptyMap()

    // Resultado
    private var resultRecorded: Boolean = false

    // ------------------------------------------------------------------
    // Intenciones de la UI
    // ------------------------------------------------------------------

    /**
     * Inicia una partida LOCAL/SOLO de demostración. Ambos lados se simulan en
     * este proceso (motor + sync local); es la demo funcional del frontend sin
     * backend. Ver el flujo completo en la KDoc de la clase.
     */
    suspend fun startLocalDemo(
        myDeck: List<CardId> = defaultDemoDeck(),
        opponentDeck: List<CardId> = defaultDemoDeck(),
    ) {
        resetSession()
        val players = listOf(myId, botPlayer)
        val snapshot = factory.newMatch(
            matchId = MatchId("local-demo"),
            players = players,
            deckByPlayer = mapOf(myId to myDeck, botPlayer to opponentDeck),
        )
        localSync = HostSyncEngine(engine, hostPlayerId = myId, clientPlayerId = botPlayer)
        localState = localSync!!.initialState(snapshot)
        myTurnBegun = false
        publishSnapshot(snapshot)
        _state.value = _state.value.copy(status = SessionStatus.ACTIVE, role = SessionRole.LOCAL_SOLO)
        // Auto-inicia el turno del jugador local para que la partida sea jugable ya.
        localBeginMyTurn()
    }

    /**
     * Inicia (crea) una partida EN LÍNEA como anfitrión. El host posee el motor;
     * sus propias acciones se encolan por el gateway y se procesan al volver por
     * el canal de acciones remotas.
     */
    suspend fun hostMatch(deck: List<CardId>): CombatSessionState {
        resetSession()
        _state.value = CombatSessionState(status = SessionStatus.CONNECTING, role = SessionRole.HOST)
        val matchId = gateway.createMatch(myId, deck).getOrElse { error ->
            _state.value = CombatSessionState(
                status = SessionStatus.ERROR, role = SessionRole.HOST, lastError = error.message,
            )
            return _state.value
        }
        hostMatchId = matchId
        activeListeners += gateway.listenHostState(::onHostState)
        activeListeners += gateway.listenHostActions(::onHostAction)
        activeListeners += gateway.listenResults(::onMatchResult)
        _state.value = _state.value.copy(status = SessionStatus.ACTIVE)
        return _state.value
    }

    /**
     * Une a una partida EN LÍNEA como cliente por su código. El cliente nunca ve
     * la mano del rival: solo recibe [ClientProjectionWire] con [CombatUiState.opponentHandSize].
     */
    suspend fun joinMatch(
        code: String,
        deck: List<CardId>,
    ): CombatSessionState {
        resetSession()
        _state.value = CombatSessionState(status = SessionStatus.CONNECTING, role = SessionRole.CLIENT)
        val matchId = gateway.joinMatch(code, myId, deck).getOrElse { error ->
            _state.value = CombatSessionState(
                status = SessionStatus.ERROR, role = SessionRole.CLIENT, lastError = error.message,
            )
            return _state.value
        }
        clientMatchId = matchId
        myCardOf = factory.buildDeck(myId, deck).second
        clientSync = ClientSync(initialVersion = 0L, playerId = myId)
        activeListeners += gateway.listenClientProjection(::onClientProjection)
        activeListeners += gateway.listenResults(::onMatchResult)
        _state.value = _state.value.copy(status = SessionStatus.ACTIVE)
        return _state.value
    }

    /**
     * Juega la carta [instanceId] (con objetivo opcional [targetPlayer]: el avatar
     * al que apunta el efecto; null = objetivo por defecto según el efecto, p. ej.
     * el rival para los ataques o el propio jugador para las curaciones).
     * En local reenvía directo al motor (validando jugabilidad); en línea la
     * traduce a un [MatchAction] y la encola por el gateway.
     */
    suspend fun playCard(instanceId: InstanceId, targetPlayer: PlayerId? = null) {
        val mode = _state.value.role
        when (mode) {
            SessionRole.LOCAL_SOLO -> localPlayCard(instanceId, targetPlayer)
            SessionRole.HOST -> enqueueHost(playCardAction(instanceId, targetPlayer))
            SessionRole.CLIENT -> enqueueClient(ActionType.PLAY_CARD, instanceId, targetPlayer)
            null -> Unit
        }
    }

    /** Termina el turno actual (del jugador local en las modalidades local/cliente). */
    suspend fun endTurn() {
        when (_state.value.role) {
            SessionRole.LOCAL_SOLO -> localEndMyTurn()
            SessionRole.HOST -> enqueueHost(endTurnAction())
            SessionRole.CLIENT -> enqueueClient(ActionType.END_TURN)
            null -> Unit
        }
    }

    /**
     * Inicia el turno del jugador local. En local el turno se auto-inicia, por lo
     * que llamar aquí es un no-op seguro; en línea encola el [ActionType.BEGIN_TURN].
     */
    suspend fun beginTurn() {
        when (_state.value.role) {
            SessionRole.LOCAL_SOLO -> if (!myTurnBegun) localBeginMyTurn()
            SessionRole.HOST -> enqueueHost(beginTurnAction())
            SessionRole.CLIENT -> enqueueClient(ActionType.BEGIN_TURN)
            null -> Unit
        }
    }

    /**
     * Abandona la sesión actual: cierra todas las suscripciones y vuelve a IDLE.
     */
    fun leaveMatch() {
        activeListeners.forEach { runCatching { it.close() } }
        activeListeners.clear()
        resetFields()
        _state.value = CombatSessionState(status = SessionStatus.IDLE)
    }

    // ------------------------------------------------------------------
    // Modo local (demo/solo)
    // ------------------------------------------------------------------

    private fun localBeginMyTurn() {
        if (finished()) return
        val result = processLocal(ActionType.BEGIN_TURN, myId, null, null)
        if (result is HostSyncEngine.HostSyncResult.Processed) {
            myTurnBegun = true
        }
    }

    private fun localPlayCard(instanceId: InstanceId, targetPlayer: PlayerId?) {
        if (finished() || !myTurnBegun) return
        // Validación preventiva vía el reducer: se evita encolar una jugada inválida.
        val current = _state.value.uiState ?: return
        val card = current.myHand.firstOrNull { it.instanceId == instanceId }
        if (card == null || !card.playable) {
            logRejection("No se puede jugar (mana insuficiente o no es jugable).")
            return
        }
        processLocal(ActionType.PLAY_CARD, myId, instanceId, targetPlayer)
    }

    private fun localEndMyTurn() {
        if (finished() || !myTurnBegun) return
        val result = processLocal(ActionType.END_TURN, myId, null, null)
        myTurnBegun = false
        if (result is HostSyncEngine.HostSyncResult.Processed && !finished()) {
            // El turno pasó al BOT: se simula su turno completo.
            playBotTurn()
        }
    }

    /**
     * Simula el turno COMPLETO del BOT: BeginTurn -> jugar cartas -> EndTurn.
     * Al terminar, si no hay ganador, se auto-inicia el turno del jugador local.
     */
    private fun playBotTurn() {
        var guard = 0
        while (!finished() && isBotTurn() && guard < 40) {
            guard++
            val begin = processLocal(ActionType.BEGIN_TURN, botPlayer, null, null)
            if (begin !is HostSyncEngine.HostSyncResult.Processed) break
            if (finished() || !isBotTurn()) break

            // Jugar hasta N cartas razonables (prioriza dañar el avatar rival del BOT).
            var cardsPlayed = 0
            while (cardsPlayed < 8 && !finished() && isBotTurn()) {
                val candidates = botCandidates()
                if (candidates.isEmpty()) break
                val (inst, target) = candidates.first()
                processLocal(ActionType.PLAY_CARD, botPlayer, inst, target)
                cardsPlayed++
            }
            if (finished() || !isBotTurn()) break

            val end = processLocal(ActionType.END_TURN, botPlayer, null, null)
            if (end is HostSyncEngine.HostSyncResult.Processed && finished()) break
        }
        // De vuelta al turno del jugador local: se auto-inicia.
        if (!finished()) localBeginMyTurn()
    }

    /** true si actualmente es el turno del BOT y la partida sigue en curso. */
    private fun isBotTurn(): Boolean {
        val snap = localState?.snapshot ?: return false
        return snap.currentPlayer == botPlayer && snap.phase == MatchSnapshot.Phase.PLAYING
    }

    /**
     * Cartas del BOT que puede jugar ahora, con el avatar objetivo preferido.
     *
     * No hay unidades que atacar (rediseño Fase 1): los efectos apuntan SIEMPRE a
     * un avatar. La política de objetivos, discriminada por [CardEffect]:
     * - [CardEffect.Attack] -> [myId]: daña el avatar del jugador local (el rival).
     * - [CardEffect.Heal] -> [botPlayer]: cura el avatar del propio BOT.
     * - [CardEffect.ApplyStatus] -> [myId]: entorpece el avatar del rival.
     * - [CardEffect.Draw] / [CardEffect.None] / [CardEffect.PassiveBuff] -> null.
     * Las cartas pasivas y las que exceden el mana disponible se descartan.
     */
    private fun botCandidates(): List<Pair<InstanceId, PlayerId?>> {
        val snap = localState?.snapshot ?: return emptyList()
        val mana = snap.manaOf(botPlayer)
        return snap.handOf(botPlayer).mapNotNull { inst ->
            val cardId = snap.cardOf[inst] ?: return@mapNotNull null
            val card = catalog.findById(cardId) ?: return@mapNotNull null
            if (card.isPassive || mana < card.cost) return@mapNotNull null
            val target = when (card.effect) {
                is CardEffect.Attack -> myId
                is CardEffect.Heal -> botPlayer
                is CardEffect.ApplyStatus -> myId
                is CardEffect.Draw -> null
                is CardEffect.None -> null
                is CardEffect.PassiveBuff -> null
            }
            inst to target
        }
    }

    /**
     * Procesa una acción contra el [HostSyncEngine] local y aplica el resultado.
     * La secuencia se lee de [HostSyncState.nextExpectedActionSeq] (autocorregida),
     * de modo que los rechazos no desincronizan la cola.
     */
    private fun processLocal(
        type: ActionType,
        player: PlayerId,
        instanceId: InstanceId?,
        targetPlayer: PlayerId?,
    ): HostSyncEngine.HostSyncResult {
        val sync = localSync ?: return HostSyncEngine.HostSyncResult.Duplicate(localState!!)
        val state = localState ?: return HostSyncEngine.HostSyncResult.Duplicate(localState!!)
        val action = MatchAction(
            playerId = player,
            actionType = type,
            payload = ActionPayload(instanceId = instanceId, targetPlayer = targetPlayer),
            sequence = state.nextExpectedActionSeq,
        )
        val result = sync.process(action, state)
        when (result) {
            is HostSyncEngine.HostSyncResult.Processed -> {
                localState = result.state
                publishSnapshot(result.state.snapshot)
                if (result.matchResult != null) finishSession(result.matchResult)
            }
            is HostSyncEngine.HostSyncResult.Duplicate -> logRejection("Acción duplicada (seq ${action.sequence}).")
            is HostSyncEngine.HostSyncResult.OutOfOrder -> logRejection("Fuera de orden (seq ${action.sequence}).")
            is HostSyncEngine.HostSyncResult.Rejected -> logRejection("Rechazada: ${result.reason}")
        }
        return result
    }

    // ------------------------------------------------------------------
    // Modo HOST en línea
    // ------------------------------------------------------------------

    private fun onHostState(state: HostSyncState) {
        val opponent = state.snapshot.players.firstOrNull { it != myId } ?: return
        hostSync = HostSyncEngine(engine, hostPlayerId = myId, clientPlayerId = opponent)
        hostState = state
        publishSnapshot(state.snapshot)
        _state.value = _state.value.copy(status = SessionStatus.ACTIVE)
    }

    /**
     * Procesa una acción (propia encolada o remota) contra el [HostSyncEngine]
     * del host. En [HostSyncEngine.HostSyncResult.Processed] se actualiza el
     * estado, se publica el snapshot y, si hay [MatchResult], se cierra sesión.
     */
    private fun onHostAction(action: MatchAction) {
        val sync = hostSync ?: return
        val state = hostState ?: return
        val result = sync.process(action, state)
        when (result) {
            is HostSyncEngine.HostSyncResult.Processed -> {
                hostState = result.state
                publishSnapshot(result.state.snapshot)
                val matchId = hostMatchId ?: result.state.snapshot.matchId
                scope.launch { gateway.publishHostSnapshot(matchId, result.state) }
                if (result.matchResult != null) finishSession(result.matchResult)
            }
            is HostSyncEngine.HostSyncResult.Duplicate ->
                logRejection("Acción duplicada (seq ${action.sequence}).")
            is HostSyncEngine.HostSyncResult.OutOfOrder ->
                logRejection("Fuera de orden (seq ${action.sequence}).")
            is HostSyncEngine.HostSyncResult.Rejected ->
                logRejection("Rechazada: ${result.reason}")
        }
    }

    private suspend fun enqueueHost(action: MatchAction) {
        val result = gateway.enqueueAction(action)
        result.onFailure { _state.value = _state.value.copy(lastError = it.message) }
    }

    private fun beginTurnAction(): MatchAction =
        MatchAction(myId, ActionType.BEGIN_TURN, ActionPayload(), nextHostSeq())

    private fun playCardAction(instanceId: InstanceId, targetPlayer: PlayerId?): MatchAction =
        MatchAction(myId, ActionType.PLAY_CARD, ActionPayload(instanceId, targetPlayer), nextHostSeq())

    private fun endTurnAction(): MatchAction =
        MatchAction(myId, ActionType.END_TURN, ActionPayload(), nextHostSeq())

    private fun nextHostSeq(): Long =
        hostState?.nextExpectedActionSeq ?: 0L

    // ------------------------------------------------------------------
    // Modo CLIENT en línea
    // ------------------------------------------------------------------

    private fun onClientProjection(projection: ClientProjectionWire) {
        val sync = clientSync ?: return
        if (!sync.shouldApply(projection)) return
        sync.markApplied(projection)
        val ui = CombatReducer.fromClientProjection(projection, catalog, myCardOf, maxLogLines)
        _state.value = _state.value.copy(
            uiState = ui,
            status = if (projection.phase == MatchSnapshot.Phase.FINISHED) SessionStatus.FINISHED else SessionStatus.ACTIVE,
        )
    }

    private suspend fun enqueueClient(
        type: ActionType,
        instanceId: InstanceId? = null,
        targetPlayer: PlayerId? = null,
    ) {
        val sync = clientSync ?: return
        val action = sync.nextAction(type, instanceId, targetPlayer)
        gateway.enqueueAction(action).onFailure {
            _state.value = _state.value.copy(lastError = it.message)
        }
    }

    // ------------------------------------------------------------------
    // Cierre de sesión / resultado
    // ------------------------------------------------------------------

    private fun onMatchResult(result: MatchResult) {
        // En cliente el desenlace puede llegar por aquí; se marca FINISHED.
        _state.value = _state.value.copy(status = SessionStatus.FINISHED)
        finishSession(result)
    }

    /**
     * Marca la partida como FINISHED, publica la vista final y registra el
     * resultado en [MatchResultSink] (una sola vez) con la XP correcta.
     */
    private fun finishSession(result: MatchResult) {
        if (resultRecorded) return
        resultRecorded = true
        val finalUi = CombatReducer.fromSnapshot(result.finalSnapshot, catalog, myId, maxLogLines)
        _state.value = _state.value.copy(
            status = SessionStatus.FINISHED,
            uiState = finalUi,
        )
        val victory = result.winner == myId
        val xp = if (victory) XP_VICTORY else XP_DEFEAT
        val summary = buildString {
            append(if (victory) "Victoria" else "Derrota")
            append(" por ${result.reason.name}")
        }
        scope.launch { resultSink.recordMatch(result.matchId, victory, xp, summary) }
    }

    private fun publishSnapshot(snapshot: MatchSnapshot) {
        _state.value = _state.value.copy(
            uiState = CombatReducer.fromSnapshot(snapshot, catalog, myId, maxLogLines),
        )
    }

    private fun logRejection(message: String) {
        val current = _state.value.rejectionLog
        _state.value = _state.value.copy(rejectionLog = (current + message).takeLast(MAX_REJECTION_LOG))
    }

    private fun finished(): Boolean =
        localState?.snapshot?.phase == MatchSnapshot.Phase.FINISHED

    private fun resetSession() {
        closeListeners()
        resetFields()
    }

    private fun resetFields() {
        localSync = null
        localState = null
        myTurnBegun = false
        hostSync = null
        hostState = null
        hostMatchId = null
        clientSync = null
        clientMatchId = null
        myCardOf = emptyMap()
        resultRecorded = false
        _state.value = CombatSessionState(status = SessionStatus.IDLE)
    }

    private fun closeListeners() {
        activeListeners.forEach { runCatching { it.close() } }
        activeListeners.clear()
    }

    companion object {
        /** XP otorgada por victoria según [com.cardclash.domain.progression.XpSource]. */
        private const val XP_VICTORY = 5
        private const val XP_DEFEAT = 2
        private const val MAX_REJECTION_LOG = 6

        /**
         * Mazo por defecto para la demo local: legal según [com.cardclash.domain.service.
         * DeckRuleService] (8 cartas no pasivas distintas + 1 pasiva extra) y sin
         * cartas de estado, para que la demo termine con daño directo.
         */
        fun defaultDemoDeck(): List<CardId> = listOf(
            CardId("attack-0"),
            CardId("attack-1"),
            CardId("attack-2"),
            CardId("attack-3"),
            CardId("attack-4"),
            CardId("attack-5"),
            CardId("heal-0"),
            CardId("draw-0"),
            CardId("passive-max_mana-+2"),
        )

        /**
         * Constructor de conveniencia con motor por defecto (catálogo real +
         * dice determinista) para la demo y pruebas.
         */
        fun demoEngine(): CombatEngine =
            CombatEngine(DefaultCardCatalog(), SeededDiceRoller(42))
    }
}
