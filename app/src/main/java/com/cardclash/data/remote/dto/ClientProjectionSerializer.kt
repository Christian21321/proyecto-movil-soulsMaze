package com.cardclash.data.remote.dto

import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.data.remote.dto.WireFields.CLIENT_HAND
import com.cardclash.data.remote.dto.WireFields.CLIENT_PLAYER
import com.cardclash.data.remote.dto.WireFields.CURRENT_PLAYER
import com.cardclash.data.remote.dto.WireFields.HERO_HEALTH
import com.cardclash.data.remote.dto.WireFields.HERO_MAX_HEALTH
import com.cardclash.data.remote.dto.WireFields.HERO_STATUSES
import com.cardclash.data.remote.dto.WireFields.LOG
import com.cardclash.data.remote.dto.WireFields.MANAS
import com.cardclash.data.remote.dto.WireFields.MATCH_ID
import com.cardclash.data.remote.dto.WireFields.NUM_CARDS_IN_HOST_HAND
import com.cardclash.data.remote.dto.WireFields.PASSIVES
import com.cardclash.data.remote.dto.WireFields.PHASE
import com.cardclash.data.remote.dto.WireFields.PLAYED_CARD_LAST_TURN
import com.cardclash.data.remote.dto.WireFields.PLAYERS
import com.cardclash.data.remote.dto.WireFields.TURN
import com.cardclash.data.remote.dto.WireFields.VERSION
import com.cardclash.data.remote.dto.WireFields.WINNER

/**
 * Serializador de la proyeccion cliente (`stateClient`, ADR-009).
 *
 * Deriva la proyeccion desde un [MatchSnapshot] completo (filtrando la mano del
 * host y exponiendo solo su conteo) y la convierte a su mapa wire.
 */
object ClientProjectionSerializer {

    /**
     * Deriva la proyeccion para [clientPlayer] a partir del [snapshot] completo.
     *
     * [numCardsInHostHand] es el tamano de la mano de todos los jugadores que NO
     * son [clientPlayer]; en una partida 1v1 corresponde al host.
     */
    fun fromSnapshot(
        snapshot: MatchSnapshot,
        clientPlayer: PlayerId,
        version: Long,
    ): ClientProjectionWire {
        val hostCards = snapshot.hands
            .filterKeys { it != clientPlayer }
            .values
            .sumOf { it.size }
        return ClientProjectionWire(
            matchId = snapshot.matchId,
            phase = snapshot.phase,
            turn = snapshot.turn,
            currentPlayer = snapshot.currentPlayer,
            players = snapshot.players,
            heroHealth = snapshot.heroHealth,
            heroMaxHealth = snapshot.heroMaxHealth,
            heroStatuses = snapshot.heroStatuses,
            passives = snapshot.passives,
            manas = snapshot.manas,
            playedCardLastTurn = snapshot.playedCardLastTurn,
            winner = snapshot.winner,
            log = snapshot.log,
            clientPlayer = clientPlayer,
            clientHand = snapshot.hands[clientPlayer].orEmpty(),
            numCardsInHostHand = hostCards,
            version = version,
        )
    }

    /** Convierte la proyeccion a su mapa wire (`stateClient`). */
    fun toWire(projection: ClientProjectionWire): Map<String, Any?> = mapOf(
        MATCH_ID to projection.matchId.value,
        PHASE to projection.phase.name,
        TURN to projection.turn.toLong(),
        CURRENT_PLAYER to projection.currentPlayer.value,
        PLAYERS to projection.players.map { it.value },
        HERO_HEALTH to projection.heroHealth.mapKeys { it.key.value }
            .mapValues { (_, value) -> value.toLong() },
        HERO_MAX_HEALTH to projection.heroMaxHealth.mapKeys { it.key.value }
            .mapValues { (_, value) -> value.toLong() },
        HERO_STATUSES to HeroStatusCodec.toStatuses(projection.heroStatuses),
        PASSIVES to HeroStatusCodec.toPassives(projection.passives),
        MANAS to projection.manas.mapKeys { it.key.value }
            .mapValues { (_, value) -> value.toLong() },
        PLAYED_CARD_LAST_TURN to projection.playedCardLastTurn.mapKeys { it.key.value },
        WINNER to projection.winner?.value,
        LOG to projection.log,
        CLIENT_PLAYER to projection.clientPlayer.value,
        CLIENT_HAND to projection.clientHand.map { it.value },
        NUM_CARDS_IN_HOST_HAND to projection.numCardsInHostHand.toLong(),
        VERSION to projection.version,
    )
}
