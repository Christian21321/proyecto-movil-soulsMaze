package com.cardclash.data.remote.sync

import com.cardclash.data.remote.dto.ClientProjectionSerializer
import com.cardclash.data.remote.dto.HostSnapshotWire
import com.cardclash.domain.model.PlayerId

/**
 * Construccion PURA del [HostWritePlan] que el host publica al difundir su
 * estado autoritativo (canal
 * [com.cardclash.domain.battle.MatchSessionGateway.publishHostSnapshot]).
 *
 * Mismo proposito que el plan que [HostSyncEngine] calcula al procesar una
 * accion, pero partiendo de un [HostSyncState] YA procesado: deriva los
 * documentos `stateHost`/`stateClient` de ADR-009 mas la metadata actualizada
 * sin tocar Firestore. Es 100% Kotlin JVM y se testea con unit tests JVM.
 */
object HostWritePlanBuilder {

    /**
     * Deriva el plan de escritura para difundir [state] desde la perspectiva
     * del host [hostId]. Devuelve null si el snapshot aun no tiene rival
     * registrado (partida sin asentar con ambos jugadores).
     */
    fun build(state: HostSyncState, hostId: PlayerId): HostWritePlan? {
        val clientId = state.snapshot.players.firstOrNull { it != hostId } ?: return null
        return HostWritePlan(
            hostSnapshotWire = HostSnapshotWire(
                version = state.version,
                processedActionSeq = state.nextExpectedActionSeq,
                snapshot = state.snapshot,
            ),
            clientProjectionWire = ClientProjectionSerializer.fromSnapshot(
                snapshot = state.snapshot,
                clientPlayer = clientId,
                version = state.version,
            ),
            metadata = MatchMetaWire.fromSnapshot(state.snapshot),
            processedActionSeq = state.nextExpectedActionSeq,
        )
    }
}