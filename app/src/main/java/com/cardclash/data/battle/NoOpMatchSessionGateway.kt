package com.cardclash.data.battle

import com.cardclash.data.remote.dto.ClientProjectionWire
import com.cardclash.data.remote.sync.HostSyncState
import com.cardclash.data.remote.sync.MatchAction
import com.cardclash.domain.battle.MatchSessionGateway
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchResult
import com.cardclash.domain.model.PlayerId
import java.io.Closeable

/**
 * Puerta de salida NO-OP para el modo LOCAL/DEMO.
 *
 * El controlador puro solo toca [MatchSessionGateway] en los modos en línea
 * ([hostMatch]/[joinMatch]); en [SessionRole.LOCAL_SOLO] toda la simulación es
 * local (motor + sync), por lo que esta implementación queda inerte. Permite
 * lanzar la demo visible sin requerir inicialización de Firebase ni backend.
 */
class NoOpMatchSessionGateway : MatchSessionGateway {

    override suspend fun createMatch(hostId: PlayerId, deck: List<CardId>): Result<MatchId> =
        Result.failure(UnsupportedOperationException("Canal NO-OP: no hay partida en línea."))

    override suspend fun joinMatch(code: String, playerId: PlayerId, deck: List<CardId>): Result<MatchId> =
        Result.failure(UnsupportedOperationException("Canal NO-OP: no hay partida en línea."))

    override suspend fun enqueueAction(action: MatchAction): Result<Unit> = Result.success(Unit)

    override fun listenHostState(onHostState: (HostSyncState) -> Unit): Closeable = Closeable { }

    override fun listenHostActions(onAction: (MatchAction) -> Unit): Closeable = Closeable { }

    override suspend fun publishHostSnapshot(matchId: MatchId, state: HostSyncState): Result<Unit> =
        Result.success(Unit)

    override fun listenClientProjection(onProjection: (ClientProjectionWire) -> Unit): Closeable =
        Closeable { }

    override fun listenResults(onResult: (MatchResult) -> Unit): Closeable = Closeable { }
}
