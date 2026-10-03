package com.cardclash.data.remote.sync

import com.cardclash.data.remote.dto.ClientProjectionWire
import com.cardclash.data.remote.dto.HostSnapshotWire

/**
 * Descripcion de todo lo que el host debe PERSISTIR tras procesar una accion
 * (ADR-009): los documentos `stateHost` y `stateClient` mas la metadata
 * `matches/{matchId}`.
 *
 * Es un plan de escritura: la logica pura ([HostSyncEngine]) lo calcula pero NO
 * toca Firestore. El adaptador ([com.cardclash.data.remote.firebase.FirestoreMatchRepository])
 * es quien ejecuta la escritura real.
 */
data class HostWritePlan(
    val hostSnapshotWire: HostSnapshotWire,
    val clientProjectionWire: ClientProjectionWire,
    val metadata: MatchMetaWire,
    /** Secuencia concreta que este plan marca como procesada. */
    val processedActionSeq: Long,
)
