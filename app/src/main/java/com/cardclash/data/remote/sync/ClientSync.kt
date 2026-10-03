package com.cardclash.data.remote.sync

import com.cardclash.data.remote.dto.ClientProjectionWire
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.PlayerId

/**
 * Orquestacion PURA del cliente sobre las proyecciones recibidas (`stateClient`).
 *
 * Es 100% Kotlin JVM (sin Firestore ni android.*): encapsula el protocolo de
 * versiones (no aplicar proyecciones obsoletas) y el registro monotono de
 * secuencia ([nextActionSeq]) que el cliente usa al construir la proxima
 * [MatchAction].
 */
class ClientSync(
    initialVersion: Long = 0L,
    private val playerId: PlayerId,
) {

    /** Ultima version recibida y aceptada de la proyeccion. */
    private var localVersion: Long = initialVersion

    /** Proxima secuencia a usar para la accion del cliente (monotona). */
    private var nextActionSeq: Long = 0L

    /**
     * Devuelve true si la proyeccion [received] es mas nueva que la local
     * (es decir, debe aplicarse). Las proyecciones obsoletas o repetidas se
     * ignoran para no regresar el estado a una version anterior.
     */
    fun shouldApply(received: ClientProjectionWire): Boolean =
        received.version > localVersion

    /** Marca la proyeccion [applied] como la vigente (actualiza la version). */
    fun markApplied(applied: ClientProjectionWire): ClientProjectionWire {
        localVersion = applied.version
        return applied
    }

    /**
     * Construye la proxima accion [ActionType] del cliente, consumiendo el
     * siguiente valor de [nextActionSeq] (monotono, garantizado sin colisiones
     * aunque se reintente). La primera accion usa la secuencia 0, coincidiendo
     * con [HostSyncEngine.initialState] del host (su primer `nextExpectedActionSeq`).
     */
    fun nextAction(
        actionType: ActionType,
        instanceId: InstanceId? = null,
        targetPlayer: PlayerId? = null,
    ): MatchAction = MatchAction(
        playerId = playerId,
        actionType = actionType,
        payload = ActionPayload(instanceId = instanceId, targetPlayer = targetPlayer),
        sequence = nextActionSeq++,
    )
}
