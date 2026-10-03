package com.cardclash.domain.battle

import com.cardclash.data.remote.dto.ClientProjectionWire
import com.cardclash.data.remote.sync.HostSyncState
import com.cardclash.data.remote.sync.MatchAction
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.PlayerId
import java.io.Closeable

/**
 * Puerto de salida de la sesión de combate hacia la infraestructura remota
 * (Firestore / FirestoreMatchRepository) — NO implementado aquí.
 *
 * Todas las operaciones que tocarían Android o Firebase quedan AISLADAS detrás
 * de esta interfaz: el [MatchSessionController] (lógica pura) depende SOLO de
 * ella. La implementación real (capa `data`) conectará estos métodos al
 * [com.cardclash.data.remote.firebase.FirestoreMatchRepository] SIN que la
 * lógica pura conozca `Task`, `DocumentSnapshot` ni `ListenerRegistration`
 * (aquí se usan `Result`, `suspend` y `java.io.Closeable`).
 *
 * # Nota de diseño respecto al protocolo ADR-009
 *
 * El host ES quien posee el [CombatEngine] y procesa las acciones de la cola,
 * por lo que la interfaz entrega al host las acciones REMOTAS crudas
 * ([listenHostActions]) y le permite publicar su estado procesado
 * ([publishHostSnapshot]). La versión ilustrativa de la especificación solo
 * mencionaba [listenHostState]; aquí se conserva esa señal (empuje del estado
 * autoritativo, incluida la inicial) y se AÑADE el canal de acciones remotas +
 * publicación, porque sin ellos el host no podría procesar la cola ni difundir
 * su estado. El cliente recibe proyecciones filtradas ([listenClientProjection])
 * que nunca revelan la mano del rival.
 */
interface MatchSessionGateway {

    /**
     * Crea una partida en la que [hostId] es el anfitrión con el mazo [deck].
     * Devuelve el [MatchId] creado (o el error) una vez que el backend acepta.
     */
    suspend fun createMatch(hostId: PlayerId, deck: List<CardId>): Result<MatchId>

    /**
     * Une a [playerId] (con su mazo [deck]) a una partida existente por su
     * código de inclusión. Devuelve el [MatchId] (o el error).
     */
    suspend fun joinMatch(code: String, playerId: PlayerId, deck: List<CardId>): Result<MatchId>

    /**
     * Encola una [MatchAction] del jugador local en la cola compartida
     * (`actions/{autoId}` de ADR-009). Devuelve éxito/error de persistencia.
     */
    suspend fun enqueueAction(action: MatchAction): Result<Unit>

    // --- Host ---

    /**
     * Canal del estado autoritativo que el host debe observar. Entrega el
     * [HostSyncState] inicial (una vez que la partida queda asentada con ambos
     * jugadores) y, en implementaciones que repliquen hacia atrás, las
     * actualizaciones confirmadas. El host parte de aquí para procesar la cola.
     */
    fun listenHostState(onHostState: (HostSyncState) -> Unit): Closeable

    /**
     * Canal de acciones REMOTAS en cola que el host debe procesar con su motor.
     * [onAction] recibe cada [MatchAction] pendiente; el controlador la resuelve
     * con [com.cardclash.data.remote.sync.HostSyncEngine].
     */
    fun listenHostActions(onAction: (MatchAction) -> Unit): Closeable

    /**
     * Publica el estado procesado del host (snapshot + secuencia) para que los
     * observadores (cliente) puedan leer la última proyección.
     */
    suspend fun publishHostSnapshot(matchId: MatchId, state: HostSyncState): Result<Unit>

    // --- Client ---

    /**
     * Canal de proyecciones filtradas del cliente (modo JOIN). Cada proyección
     * es una [ClientProjectionWire] que expone la mano del propio cliente y el
     * CONTEO de cartas del rival, nunca su mano ni los mazos.
     */
    fun listenClientProjection(onProjection: (ClientProjectionWire) -> Unit): Closeable

    // --- Both ---

    /**
     * Canal de resultados FINALES de la partida (ambos roles). Permite registrar
     * el desenlace en el histórico cuando el host o el backend lo declaran.
     */
    fun listenResults(onResult: (com.cardclash.domain.model.MatchResult) -> Unit): Closeable
}
