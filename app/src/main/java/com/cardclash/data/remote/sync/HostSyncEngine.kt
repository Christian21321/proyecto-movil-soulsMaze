package com.cardclash.data.remote.sync

import com.cardclash.data.remote.dto.ClientProjectionSerializer
import com.cardclash.data.remote.dto.HostSnapshotWire
import com.cardclash.domain.engine.CombatEngine
import com.cardclash.domain.model.MatchResult
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId

/**
 * Orquestacion PURA del host: aplica cada [MatchAction] de la cola sobre el
 * estado local usando el [CombatEngine] y calcula que se debe escribir.
 *
 * Es 100% Kotlin JVM (sin Firestore ni android.*): recibe el estado interno y una
 * accion, devuelve un resultado tipado. Toda la logica de decision (validacion de
 * secuencia, validacion semantica, traduccion a [CombatEngine.CombatAction],
 * construccion de la proyeccion cliente) vive aqui; el adaptador Firestore solo
 * ejecuta el plan de escritura que este devuelve.
 */
class HostSyncEngine(
    private val engine: CombatEngine,
    private val hostPlayerId: PlayerId,
    private val clientPlayerId: PlayerId,
) {

    /**
     * Resultado tipado del procesamiento de una accion.
     *
     * - [Processed]: la accion era la esperada y se ejecuto; contiene el plan de
     *   escritura y el nuevo estado.
     * - [Duplicate]: secuencia ya procesada ([MatchAction.sequence] menor que la
     *   esperada); se ignora sin cambiar nada.
     * - [OutOfOrder]: secuencia mayor que la esperada (hueco/pérdida); se rechaza.
     * - [Rejected]: secuencia correcta pero la jugada es ilegal semanticamente
     *   (p. ej. no es el turno del jugador); el estado NO cambia.
     */
    sealed interface HostSyncResult {
        val state: HostSyncState

        /** La accion esperada se ejecuto: hay algo que escribir. */
        data class Processed(
            override val state: HostSyncState,
            val writePlan: HostWritePlan,
            val matchResult: MatchResult?,
        ) : HostSyncResult

        /** Secuencia menor que la esperada: repetida/ignorada (idempotente). */
        data class Duplicate(override val state: HostSyncState) : HostSyncResult

        /** Secuencia mayor que la esperada: fuera de orden, se rechaza. */
        data class OutOfOrder(override val state: HostSyncState) : HostSyncResult

        /** Secuencia correcta pero jugada ilegal: se rechaza sin mutar estado. */
        data class Rejected(override val state: HostSyncState, val reason: String) : HostSyncResult
    }

    /**
     * Procesa [action] contra [state] y devuelve el resultado tipado.
     * El estado de entrada JAMAS se muta: [Processed] devuelve un estado nuevo.
     */
    fun process(action: MatchAction, state: HostSyncState): HostSyncResult {
        // 1) Validacion de secuencia (idempotencia y orden).
        val seq = action.sequence
        val expected = state.nextExpectedActionSeq
        if (seq < expected) return HostSyncResult.Duplicate(state)
        if (seq > expected) return HostSyncResult.OutOfOrder(state)

        // 2) Validacion semantica contra el snapshot local.
        val semantic = validateSemantics(action, state.snapshot)
        if (semantic != null) return HostSyncResult.Rejected(state, semantic)

        // 3) Traduccion a la accion del motor.
        val combatAction = when (action.actionType) {
            ActionType.BEGIN_TURN -> CombatEngine.CombatAction.BeginTurn
            ActionType.PLAY_CARD -> CombatEngine.CombatAction.PlayCard(
                instanceId = requireNotNull(action.payload.instanceId) {
                    "PLAY_CARD requiere instanceId en el payload."
                },
                targetPlayer = action.payload.targetPlayer,
            )
            ActionType.END_TURN -> CombatEngine.CombatAction.EndTurn
        }

        // 4) Ejecucion del motor (funcional, inmutable).
        val result = engine.applyAction(state.snapshot, combatAction)
        val newSnapshot = result.snapshot

        // 4.1) Deteccion de rechazo semantico del MOTOR (ADR-009: el motor es la
        // unica autoridad de legalidad; aqui solo corregimos la clasificacion de
        // transporte). El host NO duplica la logica de mana del motor: deja que el
        // motor rechace la jugada y detecta el no-op resultante.
        //
        // Para PLAY_CARD, si el estado de JUEGO no cambio (mana, mano, vida del
        // avatar, estados, pasivas, fase, turno identicos) y el log del resultado
        // incorporo un motivo de
        // validacion, la jugada fue rechazada por el motor (p. ej. mana
        // insuficiente o jugador congelado). En ese caso NO avanzamos la secuencia
        // ni la version y NO generamos writePlan/publicacion: el cliente puede
        // reintentar en el mismo turno con la misma seq.
        //
        // Se compara SOLO el estado de juego (ignorando el log, que es metadato de
        // transporte) y se detecta la razon a partir de la ultima linea nueva del
        // log. Los BEGIN_TURN/END_TURN con no-op legitimo (p. ej. FROST) NO se
        // reclasifican: solo PLAY_CARD rechazado por el motor es un "Rejected".
        if (action.actionType == ActionType.PLAY_CARD &&
            gameStateUnchanged(state.snapshot, newSnapshot)
        ) {
            val reason = rejectionReason(state.snapshot, newSnapshot)
            if (reason != null) return HostSyncResult.Rejected(state, reason)
        }

        // 5) Nuevo estado: secuencia avanzada y version incrementada.
        val newSeq = expected + 1
        val newVersion = state.version + 1
        val newState = HostSyncState(
            snapshot = newSnapshot,
            nextExpectedActionSeq = newSeq,
            version = newVersion,
        )

        // 6) Plan de escritura (sin persistir todavia).
        val hostWire = HostSnapshotWire(version = newVersion, processedActionSeq = seq, snapshot = newSnapshot)
        val clientWire = ClientProjectionSerializer.fromSnapshot(newSnapshot, clientPlayerId, newVersion)
        val writePlan = HostWritePlan(
            hostSnapshotWire = hostWire,
            clientProjectionWire = clientWire,
            metadata = MatchMetaWire.fromSnapshot(newSnapshot),
            processedActionSeq = seq,
        )
        return HostSyncResult.Processed(newState, writePlan, result.result)
    }

    /** Construye el estado inicial del host sobre un [snapshot] ya creado. */
    fun initialState(snapshot: MatchSnapshot): HostSyncState = HostSyncState(
        snapshot = snapshot,
        nextExpectedActionSeq = 0L,
        version = 0L,
    )

    private fun validateSemantics(action: MatchAction, snapshot: MatchSnapshot): String? {
        if (action.playerId != snapshot.currentPlayer) {
            return "No es el turno de ${action.playerId}."
        }
        if (snapshot.phase != MatchSnapshot.Phase.PLAYING) {
            return "La partida no esta en curso."
        }
        when (action.actionType) {
            ActionType.PLAY_CARD -> {
                val instanceId = action.payload.instanceId
                    ?: return "PLAY_CARD sin instanceId."
                if (snapshot.handOf(action.playerId).none { it == instanceId }) {
                    return "La carta $instanceId no esta en la mano de ${action.playerId}."
                }
            }
            ActionType.BEGIN_TURN, ActionType.END_TURN -> Unit
        }
        return null
    }

    /**
     * true si el estado de JUEGO entre [before] y [after] es identico, ignorando
     * los metadatos de transporte del snapshot. La comparacion se centra en lo que
     * define el estado real de la partida: vida/mana del avatar, mana, mano,
     * estados y pasivas del avatar, fase, turno y jugador actual. Se excluye
     * deliberadamente `log` (diario de transporte) para detectar no-ops con motivo
     * registrado. Tras la Fase 2a ya no hay tableros de unidades que comparar.
     */
    private fun gameStateUnchanged(before: MatchSnapshot, after: MatchSnapshot): Boolean =
        before.manas == after.manas &&
            before.hands == after.hands &&
            before.heroHealth == after.heroHealth &&
            before.heroMaxHealth == after.heroMaxHealth &&
            before.heroStatuses == after.heroStatuses &&
            before.passives == after.passives &&
            before.phase == after.phase &&
            before.turn == after.turn &&
            before.currentPlayer == after.currentPlayer

    /**
     * Extrae la razon de un rechazo del motor si el resultado [after] anadio una
     * linea al log respecto a [before]. En un no-op por validacion el motor solo
     * adjunta el motivo como ultima linea nueva ([CombatEngine] usa
     * [MatchSnapshot.withLog]), asi que la primera linea nueva es la razon.
     */
    private fun rejectionReason(before: MatchSnapshot, after: MatchSnapshot): String? {
        if (after.log.size <= before.log.size) return null
        return after.log[before.log.size]
    }
}
