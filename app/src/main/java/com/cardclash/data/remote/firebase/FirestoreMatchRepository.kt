package com.cardclash.data.remote.firebase

import com.cardclash.data.remote.dto.ClientProjectionDeserializer
import com.cardclash.data.remote.dto.ClientProjectionSerializer
import com.cardclash.data.remote.dto.ClientProjectionWire
import com.cardclash.data.remote.dto.HostSnapshotWire
import com.cardclash.data.remote.sync.ActionCodec
import com.cardclash.data.remote.sync.ActionFields
import com.cardclash.data.remote.sync.HostSyncEngine
import com.cardclash.data.remote.sync.HostSyncState
import com.cardclash.data.remote.sync.HostWritePlan
import com.cardclash.data.remote.sync.MatchAction
import com.cardclash.data.remote.sync.MatchMetaFields
import com.cardclash.data.remote.sync.MatchMetaWire
import com.cardclash.data.remote.sync.MatchMetadata
import com.cardclash.data.remote.sync.MatchMetadataCodec
import com.cardclash.data.remote.sync.MatchStatus
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.PlayerId
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot

/**
 * Adaptador FINO de Firestore para el esquema bifurcado de ADR-009.
 *
 * Es la Parte 2 de la capa de sincronizacion: toca la SDK de Firestore (emulador /
 * instrumentado) pero contiene CERO logica de decision. Toda la logica pura
 * (validacion de secuencia, semantica, traduccion a [com.cardclash.domain.engine.CombatEngine.CombatAction]
 * y construccion de la proyeccion) vive en [HostSyncEngine] (Parte 1, JVM puro).
 *
 * [FirebaseFirestore] se INYECTA: esta clase NO inicializa FirebaseApp (eso lo
 * carga `google-services.json` o el setup documentado en `docs/firebase-setup.md`).
 */
class FirestoreMatchRepository(
    private val firestore: FirebaseFirestore,
    private val hostSync: HostSyncEngine,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** Estado interno vigente del host (mutado solo desde aqui al reaplicar). */
    @Volatile
    private var hostState: HostSyncState? = null

    fun currentHostState(): HostSyncState? = hostState

    fun setHostState(state: HostSyncState) {
        hostState = state
    }

    // ---------------------------------------------------------------------
    // Creacion y union
    // ---------------------------------------------------------------------

    /** Crea la partida: escribe la metadata `matches/{matchId}` y el matchCode. */
    fun createMatch(meta: MatchMetadata): Task<Void> {
        val ref = firestore.document(FirestorePaths.match(meta.matchId.value))
        return ref.set(MatchMetadataCodec.toWire(meta))
    }

    /** Resuelve el [matchId] a partir del [matchCode] (consulta unica por codigo). */
    fun resolveMatchIdByCode(
        matchCode: String,
        onFound: (MatchId?) -> Unit,
    ): Task<QuerySnapshot> {
        val task = firestore.collection(FirestorePaths.MATCHES)
            .whereEqualTo(MatchMetaFields.MATCH_CODE, matchCode)
            .limit(1)
            .get()
        task.addOnSuccessListener { snap ->
            onFound(snap.documents.firstOrNull()?.id?.let(::MatchId))
        }
        return task
    }

    /**
     * Union del cliente por [matchId]: una UNICA transaccion que reclama la plaza
     * de clientId (si aun no estaba ocupada) y activa la partida.
     */
    fun joinMatch(matchId: String, clientId: PlayerId): Task<Unit> {
        val ref = firestore.document(FirestorePaths.match(matchId))
        return firestore.runTransaction<Unit> { txn ->
            val snap = txn.get(ref)
            if (snap.exists() && snap.getString(MatchMetaFields.CLIENT_ID) == null) {
                txn.update(
                    ref,
                    mapOf(
                        MatchMetaFields.CLIENT_ID to clientId.value,
                        MatchMetaFields.STATUS to MatchStatus.ACTIVE.name,
                        MatchMetaFields.UPDATED_AT to clock(),
                    ),
                )
            }
            Unit
        }
    }

    /** Lee la metadata completa de una partida. */
    fun loadMetadata(matchId: String, onLoaded: (MatchMetadata) -> Unit): Task<DocumentSnapshot> {
        val task = firestore.document(FirestorePaths.match(matchId)).get()
        task.addOnSuccessListener { snap ->
            val data = snap.data
            if (data != null) {
                onLoaded(MatchMetadataCodec.fromWire(data))
            }
        }
        return task
    }

    // ---------------------------------------------------------------------
    // Publicacion de estado (host)
    // ---------------------------------------------------------------------

    /**
     * Persiste un [HostWritePlan] de forma atomica (WriteBatch): escribe
     * `stateHost/current`, `stateClient/current` y la metadata actualizada.
     */
    fun publishResolution(plan: HostWritePlan): Task<Void> {
        val matchId = plan.metadata.matchId.value
        val batch = firestore.batch()
        batch.set(
            firestore.document(FirestorePaths.stateHost(matchId)),
            plan.hostSnapshotWire.toWire(),
        )
        batch.set(
            firestore.document(FirestorePaths.stateClient(matchId)),
            ClientProjectionSerializer.toWire(plan.clientProjectionWire),
        )
        batch.update(
            firestore.document(FirestorePaths.match(matchId)),
            metaUpdateOf(plan.metadata),
        )
        return batch.commit()
    }

    private fun metaUpdateOf(meta: MatchMetaWire): Map<String, Any?> = mapOf(
        MatchMetaFields.PHASE to meta.phase.name,
        MatchMetaFields.TURN to meta.turn.toLong(),
        MatchMetaFields.CURRENT_PLAYER to meta.currentPlayer.value,
        MatchMetaFields.WINNER to meta.winner?.value,
        MatchMetaFields.STATUS to meta.status.name,
        MatchMetaFields.UPDATED_AT to clock(),
    )

    /** Lee `stateHost` para la reconstruccion del host (p.ej. al perder memoria). */
    fun loadHostSnapshot(
        matchId: String,
        onLoaded: (HostSnapshotWire) -> Unit,
    ): Task<DocumentSnapshot> {
        val task = firestore.document(FirestorePaths.stateHost(matchId)).get()
        task.addOnSuccessListener { snap ->
            val data = snap.data
            if (data != null) {
                onLoaded(HostSnapshotWire.fromWire(data))
            }
        }
        return task
    }

    // ---------------------------------------------------------------------
    // Escucha del cliente (stateClient)
    // ---------------------------------------------------------------------

    /** Escucha la proyeccion `stateClient` y entrega cada actualizacion. */
    fun listenStateClient(
        matchId: String,
        onProjection: (ClientProjectionWire) -> Unit,
    ): ListenerRegistration {
        val ref = firestore.document(FirestorePaths.stateClient(matchId))
        return ref.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val data = snapshot?.data
            if (data != null) {
                onProjection(ClientProjectionDeserializer.fromWire(data))
            }
        }
    }

    // ---------------------------------------------------------------------
    // Cola de acciones (cliente envia, host procesa con la Parte 1)
    // ---------------------------------------------------------------------

    /** El cliente encola una accion en `actions/{autoId}`. */
    fun enqueueAction(matchId: String, action: MatchAction): Task<Void> {
        val ref = firestore.collection(FirestorePaths.actions(matchId)).document()
        val wire = ActionCodec.toWire(action) + mapOf(
            ActionFields.TIMESTAMP to clock(),
            ActionFields.STATUS to ACTION_STATUS_PENDING,
        )
        return ref.set(wire)
    }

    /**
     * El host escucha la cola de acciones pendientes y, para cada una, delega en
     * [HostSyncEngine.process] (Parte 1). Si resulta en un [HostSyncEngine.HostSyncResult.Processed],
     * publica el plan, marca la accion y notifica el nuevo estado del host.
     */
    fun listenActions(
        matchId: String,
        onStateChanged: (HostSyncState) -> Unit,
    ): ListenerRegistration {
        val query = firestore.collection(FirestorePaths.actions(matchId))
            .whereEqualTo(ActionFields.STATUS, ACTION_STATUS_PENDING)
            .orderBy(ActionFields.SEQUENCE)
            .limit(1)
        return query.addSnapshotListener { snapshots, error ->
            if (error != null) return@addSnapshotListener
            val doc = snapshots?.documents?.firstOrNull()
            if (doc == null || !doc.exists()) return@addSnapshotListener
            val data = doc.data ?: return@addSnapshotListener

            val state = hostState ?: return@addSnapshotListener
            val action = runCatching { ActionCodec.fromWire(data) }.getOrNull()
                ?: return@addSnapshotListener

            val result = hostSync.process(action, state)
            when (result) {
                is HostSyncEngine.HostSyncResult.Processed -> {
                    publishResolution(result.writePlan)
                    setActionStatus(matchId, doc.id, ACTION_STATUS_PROCESSED)
                    hostState = result.state
                    onStateChanged(result.state)
                }
                is HostSyncEngine.HostSyncResult.Duplicate -> {
                    // Idempotencia: ya procesada, solo se confirma el marcado.
                    setActionStatus(matchId, doc.id, ACTION_STATUS_PROCESSED)
                }
                is HostSyncEngine.HostSyncResult.OutOfOrder -> {
                    // Hueco de secuencia: se deja pendiente para no perder acciones.
                    Unit
                }
                is HostSyncEngine.HostSyncResult.Rejected -> {
                    setActionStatus(matchId, doc.id, ACTION_STATUS_REJECTED, result.reason)
                }
            }
        }
    }

    /** Marca el final/abandono de la partida en la metadata. */
    fun markAbandoned(matchId: String): Task<Void> {
        val ref = firestore.document(FirestorePaths.match(matchId))
        return ref.update(
            MatchMetaFields.STATUS,
            MatchStatus.ABANDONED.name,
            MatchMetaFields.UPDATED_AT,
            clock(),
        )
    }

    private fun setActionStatus(
        matchId: String,
        actionDocId: String,
        status: String,
        result: String? = null,
    ) {
        val ref = firestore.collection(FirestorePaths.actions(matchId)).document(actionDocId)
        val update = mutableMapOf<String, Any>(ActionFields.STATUS to status)
        if (result != null) update[ActionFields.RESULT] = result
        ref.update(update)
    }

    companion object {
        private const val ACTION_STATUS_PENDING = "PENDING"
        private const val ACTION_STATUS_PROCESSED = "PROCESSED"
        private const val ACTION_STATUS_REJECTED = "REJECTED"
    }
}
