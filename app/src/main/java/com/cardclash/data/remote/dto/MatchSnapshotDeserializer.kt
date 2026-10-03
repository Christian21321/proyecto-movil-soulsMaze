package com.cardclash.data.remote.dto

import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.data.remote.dto.WireFields.BASE_MAX_MANA
import com.cardclash.data.remote.dto.WireFields.CARD_OF
import com.cardclash.data.remote.dto.WireFields.CURRENT_PLAYER
import com.cardclash.data.remote.dto.WireFields.DECKS
import com.cardclash.data.remote.dto.WireFields.FULL_DECKS
import com.cardclash.data.remote.dto.WireFields.HANDS
import com.cardclash.data.remote.dto.WireFields.HERO_HEALTH
import com.cardclash.data.remote.dto.WireFields.HERO_MAX_HEALTH
import com.cardclash.data.remote.dto.WireFields.HERO_STATUSES
import com.cardclash.data.remote.dto.WireFields.LOG
import com.cardclash.data.remote.dto.WireFields.MANAS
import com.cardclash.data.remote.dto.WireFields.MATCH_ID
import com.cardclash.data.remote.dto.WireFields.PHASE
import com.cardclash.data.remote.dto.WireFields.PASSIVES
import com.cardclash.data.remote.dto.WireFields.PLAYED_CARD_LAST_TURN
import com.cardclash.data.remote.dto.WireFields.PLAYERS
import com.cardclash.data.remote.dto.WireFields.TURN
import com.cardclash.data.remote.dto.WireFields.WINNER

/**
 * Deserializador del wire `stateHost` hacia un [MatchSnapshot] completo.
 *
 * Es la ruta de reconstruccion (host) e incluye ambas manos, mazos, [cardOf] y
 * el estado del avatar (vida, estados y pasivas), de modo que el round-trip
 * `snapshot -> wire -> snapshot` no pierde campos relevantes para el motor.
 */
object MatchSnapshotDeserializer {

    fun fromWire(map: Map<String, Any?>): MatchSnapshot {
        val players = (map[PLAYERS] as? List<*>)?.map { PlayerId(it as String) }
            ?: error("Campo '$PLAYERS' ausente o no es List<String> en el wire.")

        @Suppress("UNCHECKED_CAST")
        fun instanceListMap(key: String): Map<PlayerId, List<InstanceId>> {
            val raw = WireRead.stringMap(map, key)
            return raw.mapKeys { (keyName, _) -> PlayerId(keyName) }
                .mapValues { (_, values) ->
                    (values as List<*>).map { InstanceId(it as String) }
                }
        }

        @Suppress("UNCHECKED_CAST")
        fun intMap(key: String): Map<PlayerId, Int> =
            WireRead.stringMap(map, key)
                .mapKeys { (keyName, _) -> PlayerId(keyName) }
                .mapValues { (_, value) -> (value as Number).toInt() }

        val heroHealth = intMap(HERO_HEALTH)
        val heroMaxHealth = intMap(HERO_MAX_HEALTH)
        val heroStatuses = HeroStatusCodec.fromStatuses(WireRead.stringMap(map, HERO_STATUSES))
        val passives = HeroStatusCodec.fromPassives(WireRead.stringMap(map, PASSIVES))

        @Suppress("UNCHECKED_CAST")
        val manasRaw = WireRead.stringMap(map, MANAS)
        val manas = manasRaw.mapKeys { (keyName, _) -> PlayerId(keyName) }
            .mapValues { (_, value) -> (value as Number).toInt() }

        @Suppress("UNCHECKED_CAST")
        val playedRaw = WireRead.stringMap(map, PLAYED_CARD_LAST_TURN)
        val playedCardLastTurn = playedRaw.mapKeys { (keyName, _) -> PlayerId(keyName) }
            .mapValues { (_, value) -> value as Boolean }

        @Suppress("UNCHECKED_CAST")
        val cardOf = WireRead.stringMap(map, CARD_OF)
            .mapKeys { (keyName, _) -> InstanceId(keyName) }
            .mapValues { (_, value) -> CardId(value as String) }

        val winner = (map[WINNER] as? String)?.let { PlayerId(it) }

        return MatchSnapshot(
            matchId = IdCodec.rehydrate<MatchId>(WireRead.requireStr(map, MATCH_ID))
                ?: error("Campo '$MATCH_ID' ausente en el wire."),
            phase = MatchSnapshot.Phase.valueOf(WireRead.requireStr(map, PHASE)),
            turn = WireRead.int(map, TURN),
            currentPlayer = IdCodec.rehydrate<PlayerId>(WireRead.requireStr(map, CURRENT_PLAYER))
                ?: error("Campo '$CURRENT_PLAYER' ausente en el wire."),
            players = players,
            hands = instanceListMap(HANDS),
            decks = instanceListMap(DECKS),
            fullDecks = instanceListMap(FULL_DECKS),
            heroHealth = heroHealth,
            heroMaxHealth = heroMaxHealth,
            heroStatuses = heroStatuses,
            passives = passives,
            manas = manas,
            baseMaxMana = (map[BASE_MAX_MANA] as? Number)?.toInt() ?: 10,
            playedCardLastTurn = playedCardLastTurn,
            cardOf = cardOf,
            winner = winner,
            log = (map[LOG] as? List<*>)?.map { it as String } ?: emptyList(),
        )
    }
}
