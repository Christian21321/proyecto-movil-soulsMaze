package com.cardclash.data.remote.firebase

import com.cardclash.data.remote.dto.ClientProjectionWire
import com.cardclash.data.remote.dto.HostSnapshotWire
import com.cardclash.data.remote.sync.ActionCodec
import com.cardclash.data.remote.sync.ActionFields
import com.cardclash.data.remote.sync.HostSyncState
import com.cardclash.data.remote.sync.HostWritePlanBuilder
import com.cardclash.data.remote.sync.MatchAction
import com.cardclash.data.remote.sync.MatchMetadata
import com.cardclash.data.remote.sync.MatchStatus
import com.cardclash.domain.battle.MatchSessionGateway
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchResult
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.WinReason
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.io.Closeable
import java.util.concurrent.ThreadLocalRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación de la capa de datos del puerto [MatchSessionGateway] sobre
 * [FirestoreMatchRepository] (esquema bifurcado de ADR-009).
 *
 * Es el adaptador fino que conecta el controlador puro de combate con Firestore
 * SIN que la lógica de dominio conozca `Task`, `DocumentSnapshot` ni
 * `ListenerRegistration`:
 * - Los `Task` de Google se adaptan a `suspend`/`Result` mediante los helpers
 *   [awaitUnit]/[awaitResult], que bloquean con [Tasks.await] sobre
 *   [Dispatchers.IO] y traducen el resultado. En particular, un `Task<Void>`
 *   (escrituras del repositorio) se mapea SIEMPRE a `Result<Unit>`, nunca a
 *   `Result<Void>` (cuyo "success" sería null e inválido).
 * - Los `ListenerRegistration` se exponen como [java.io.Closeable] para poder
 *   desuscribirlos en `leaveMatch`/`onCleared`.
 *
 * # Estado del modo en línea
 * Este modo queda CONECTADO pero NO EJECUTABLE sin backend (estrategia WARN,
 * sin `google-services.json`). Para la demo visible la app usa el modo
 * [com.cardclash.domain.battle.SessionRole.LOCAL_SOLO] con una puerta NO-OP.
 */
class FirestoreMatchSessionGateway(
    private val firestore: FirebaseFirestore,
    private val repository: FirestoreMatchRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : MatchSessionGateway {

    /** Identidad de la partida vigente en este proceso, fijada al crear/uni. */
    private var currentMatchId: MatchId? = null

    /** Identidad del anfitrión (para derivar el rival al publicar el snapshot). */
    private var currentHostId: PlayerId? = null

    // ---------------------------------------------------------------------
    // Creación y unión
    // ---------------------------------------------------------------------

    override suspend fun createMatch(hostId: PlayerId, deck: List<CardId>): Result<MatchId> {
        val matchId = newMatchId()
        val meta = MatchMetadata(
            matchId = matchId,
            hostId = hostId,
            clientId = null,
            matchCode = newMatchCode(),
            phase = MatchSnapshot.Phase.PREPARING,
            turn = 0,
            currentPlayer = hostId,
            winner = null,
            status = MatchStatus.WAITING,
            createdAt = clock(),
            updatedAt = clock(),
        )
        // createMatch devuelve Task<Void>: se mapea a Result<Unit> y luego a MatchId.
        return repository.createMatch(meta).awaitUnit().onSuccess {
            currentMatchId = matchId
            currentHostId = hostId
        }.map { matchId }
    }

    override suspend fun joinMatch(code: String, playerId: PlayerId, deck: List<CardId>): Result<MatchId> {
        val matchId = resolveMatchId(code).getOrElse { return Result.failure(it) }
        // joinMatch devuelve Task<Unit> (transacción de reclamo de plaza): Result<Unit>.
        return repository.joinMatch(matchId.value, playerId).awaitUnit().onSuccess {
            currentMatchId = matchId
        }.map { matchId }
    }

    override suspend fun enqueueAction(action: MatchAction): Result<Unit> =
        currentMatchId?.let { id -> repository.enqueueAction(id.value, action).awaitUnit() }
            ?: Result.failure(IllegalStateException("No hay partida en línea activa para encolar acciones."))

    // ---------------------------------------------------------------------
    // Host
    // ---------------------------------------------------------------------

    override fun listenHostState(onHostState: (HostSyncState) -> Unit): Closeable {
        val matchId = currentMatchId ?: return Closeable { }
        val registration: ListenerRegistration = firestore
            .document(FirestorePaths.stateHost(matchId.value))
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val data = snapshot?.data ?: return@addSnapshotListener
                val wire = runCatching { HostSnapshotWire.fromWire(data) }.getOrNull()
                    ?: return@addSnapshotListener
                val snap = wire.snapshot ?: return@addSnapshotListener
                onHostState(HostSyncState(snap, wire.processedActionSeq, wire.version))
            }
        return Closeable { registration.remove() }
    }

    override fun listenHostActions(onAction: (MatchAction) -> Unit): Closeable {
        val matchId = currentMatchId ?: return Closeable { }
        val ref = firestore.collection(FirestorePaths.actions(matchId.value))
        val query = ref
            .whereEqualTo(ActionFields.STATUS, ACTION_PENDING)
            .orderBy(ActionFields.SEQUENCE)
            .limit(1)
        val registration = query.addSnapshotListener { snapshots, error ->
            if (error != null) return@addSnapshotListener
            val doc = snapshots?.documents?.firstOrNull() ?: return@addSnapshotListener
            if (!doc.exists()) return@addSnapshotListener
            val map = doc.data ?: return@addSnapshotListener
            val action = runCatching { ActionCodec.fromWire(map) }.getOrNull()
                ?: return@addSnapshotListener
            onAction(action)
        }
        return Closeable { registration.remove() }
    }

    override suspend fun publishHostSnapshot(matchId: MatchId, state: HostSyncState): Result<Unit> {
        val hostId = currentHostId
            ?: return Result.failure(IllegalStateException("No se conoce el anfitrión de la partida."))
        // El plan de escritura es lógica PURA (HostWritePlanBuilder, JVM-testable).
        val plan = HostWritePlanBuilder.build(state, hostId)
            ?: return Result.failure(IllegalStateException("El rival aún no se ha unido a la partida."))
        // publishResolution devuelve Task<Void>: Result<Unit>.
        return repository.publishResolution(plan).awaitUnit()
    }

    // ---------------------------------------------------------------------
    // Cliente
    // ---------------------------------------------------------------------

    override fun listenClientProjection(onProjection: (ClientProjectionWire) -> Unit): Closeable {
        val matchId = currentMatchId ?: return Closeable { }
        val registration = repository.listenStateClient(matchId.value) { projection ->
            onProjection(projection)
        }
        return Closeable { registration.remove() }
    }

    // ---------------------------------------------------------------------
    // Ambos roles: resultados
    // ---------------------------------------------------------------------

    /**
     * Observa `stateHost` y, cuando el snapshot entra en [MatchSnapshot.Phase.FINISHED]
     * con ganador, reconstruye un [MatchResult]. El motivo no viaja en el wire,
     * por lo que se usa [WinReason.OPPONENT_DEFEATED] de forma predeterminada.
     */
    override fun listenResults(onResult: (MatchResult) -> Unit): Closeable {
        val matchId = currentMatchId ?: return Closeable { }
        val registration: ListenerRegistration = firestore
            .document(FirestorePaths.stateHost(matchId.value))
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val data = snapshot?.data ?: return@addSnapshotListener
                val wire = runCatching { HostSnapshotWire.fromWire(data) }.getOrNull()
                    ?: return@addSnapshotListener
                val snap = wire.snapshot ?: return@addSnapshotListener
                val winner = snap.winner ?: return@addSnapshotListener
                if (snap.phase == MatchSnapshot.Phase.FINISHED) {
                    onResult(
                        MatchResult(
                            matchId = snap.matchId,
                            winner = winner,
                            reason = WinReason.OPPONENT_DEFEATED,
                            finalSnapshot = snap,
                        ),
                    )
                }
            }
        return Closeable { registration.remove() }
    }

    // ---------------------------------------------------------------------
    // Extras de la capa de datos
    // ---------------------------------------------------------------------

    /** Marca la partida vigente como abandonada en la metadata. */
    suspend fun markAbandoned(): Result<Unit> {
        val matchId = currentMatchId
            ?: return Result.success(Unit)
        // markAbandoned devuelve Task<Void>: Result<Unit>.
        return repository.markAbandoned(matchId.value).awaitUnit().also { currentMatchId = null }
    }

    /** Cierra el estado de sesión en memoria del gateway. */
    fun clearSession() {
        currentMatchId = null
        currentHostId = null
    }

    // ---------------------------------------------------------------------
    // Adaptadores Task -> suspend/Result
    // ---------------------------------------------------------------------

    /**
     * Bloquea [Dispatchers.IO] con [Tasks.await] y traduce el resultado a
     * [Result]. Solo para tareas con resultado NO nulo (`Task<Unit>`, etc.);
     * las escrituras `Task<Void>` deben usar [awaitUnit].
     */
    private suspend fun <T> Task<T>.awaitResult(): Result<T> = withContext(Dispatchers.IO) {
        runCatching { Tasks.await(this@awaitResult) }.fold(
            onSuccess = { value ->
                if (value != null) {
                    Result.success(value)
                } else {
                    Result.failure(
                        IllegalStateException("Tarea de Firestore devolvió null (usar awaitUnit para Task<Void>)."),
                    )
                }
            },
            onFailure = { Result.failure(it) },
        )
    }

    /**
     * Adapta un `Task<Void>` (escritura) a [Result]<Unit>: cualquier éxito se
     * mapea a `Result.success(Unit)` (nunca a un "success null" inválido).
     */
    private suspend fun Task<*>.awaitUnit(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { Tasks.await(this@awaitUnit) }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) },
        )
    }

    /** Resuelve el [MatchId] por código leyendo el propio [Task]<QuerySnapshot>. */
    private suspend fun resolveMatchId(code: String): Result<MatchId> = withContext(Dispatchers.IO) {
        runCatching {
            val snap = Tasks.await(repository.resolveMatchIdByCode(code) { })
            snap.documents.firstOrNull()?.id?.let(::MatchId)
                ?: throw IllegalStateException("No se encontró una partida con el código $code")
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) },
        )
    }

    // ---------------------------------------------------------------------
    // Utils de identidad
    // ---------------------------------------------------------------------

    private fun newMatchId(): MatchId = MatchId("match-${ThreadLocalRandom.current().nextLong(1_000_000, 999_999_999)}")

    private fun newMatchCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sb = StringBuilder(4)
        repeat(4) { sb.append(alphabet[ThreadLocalRandom.current().nextInt(alphabet.length)]) }
        return sb.toString()
    }

    private companion object {
        const val ACTION_PENDING = "PENDING"
    }
}