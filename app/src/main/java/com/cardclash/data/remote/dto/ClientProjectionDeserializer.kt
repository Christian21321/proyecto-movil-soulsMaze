package com.cardclash.data.remote.dto

import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
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
 * Deserializador del wire `stateClient` hacia la [ClientProjectionWire].
 */
object ClientProjectionDeserializer {

    fun fromWire(map: Map<String, Any?>): ClientProjectionWire {
        @Suppress("UNCHECKED_CAST")
        val players = (map[PLAYERS] as? List<*>)?.map { PlayerId(it as String) }
            ?: error("Campo '$PLAYERS' ausente o no es List<String> en el wire.")

        @Suppress("UNCHECKED_CAST")
        val manas = WireRead.stringMap(map, MANAS)
            .mapKeys { (keyName, _) -> PlayerId(keyName) }
            .mapValues { (_, value) -> (value as Number).toInt() }

        @Suppress("UNCHECKED_CAST")
        val played = WireRead.stringMap(map, PLAYED_CARD_LAST_TURN)
            .mapKeys { (keyName, _) -> PlayerId(keyName) }
            .mapValues { (_, value) -> value as Boolean }

        @Suppress("UNCHECKED_CAST")
        val clientHand = (map[CLIENT_HAND] as? List<*>)?.map { InstanceId(it as String) }
            ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        fun intMap(key: String): Map<PlayerId, Int> =
            WireRead.stringMap(map, key)
                .mapKeys { (keyName, _) -> PlayerId(keyName) }
                .mapValues { (_, value) -> (value as Number).toInt() }

        return ClientProjectionWire(
            matchId = IdCodec.rehydrate<MatchId>(WireRead.requireStr(map, MATCH_ID))
                ?: error("Campo '$MATCH_ID' ausente en el wire."),
            phase = MatchSnapshot.Phase.valueOf(WireRead.requireStr(map, PHASE)),
            turn = WireRead.int(map, TURN),
            currentPlayer = IdCodec.rehydrate<PlayerId>(WireRead.requireStr(map, CURRENT_PLAYER))
                ?: error("Campo '$CURRENT_PLAYER' ausente en el wire."),
            players = players,
            heroHealth = intMap(HERO_HEALTH),
            heroMaxHealth = intMap(HERO_MAX_HEALTH),
            heroStatuses = HeroStatusCodec.fromStatuses(WireRead.stringMap(map, HERO_STATUSES)),
            passives = HeroStatusCodec.fromPassives(WireRead.stringMap(map, PASSIVES)),
            manas = manas,
            playedCardLastTurn = played,
            winner = (map[WINNER] as? String)?.let { PlayerId(it) },
            log = (map[LOG] as? List<*>)?.map { it as String } ?: emptyList(),
            clientPlayer = IdCodec.rehydrate<PlayerId>(WireRead.requireStr(map, CLIENT_PLAYER))
                ?: error("Campo '$CLIENT_PLAYER' ausente en el wire."),
            clientHand = clientHand,
            numCardsInHostHand = WireRead.int(map, NUM_CARDS_IN_HOST_HAND),
            version = WireRead.long(map, VERSION),
        )
    }
}
