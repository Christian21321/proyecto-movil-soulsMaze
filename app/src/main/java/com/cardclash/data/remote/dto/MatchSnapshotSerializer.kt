package com.cardclash.data.remote.dto

import com.cardclash.domain.model.MatchSnapshot
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
 * Serializador del [MatchSnapshot] completo hacia su representacion wire.
 *
 * Produce el cuerpo del documento `stateHost` (ADR-009): TODOS los campos del
 * snapshot, incluyendo ambas manos, mazos, [cardOf] y el estado del avatar
 * (vida actual/maxima, estados activos y pasivas). Los metadatos de control
 * (`version`, `processedActionSeq`) no forman parte del snapshot y se adjuntan
 * en [HostSnapshotWire].
 */
object MatchSnapshotSerializer {

    fun toWire(snapshot: MatchSnapshot): Map<String, Any?> = mapOf(
        MATCH_ID to snapshot.matchId.value,
        PHASE to snapshot.phase.name,
        TURN to snapshot.turn.toLong(),
        CURRENT_PLAYER to snapshot.currentPlayer.value,
        PLAYERS to snapshot.players.map { it.value },
        HANDS to snapshot.hands.mapKeys { it.key.value }
            .mapValues { (_, ids) -> ids.map { it.value } },
        DECKS to snapshot.decks.mapKeys { it.key.value }
            .mapValues { (_, ids) -> ids.map { it.value } },
        FULL_DECKS to snapshot.fullDecks.mapKeys { it.key.value }
            .mapValues { (_, ids) -> ids.map { it.value } },
        HERO_HEALTH to snapshot.heroHealth.mapKeys { it.key.value }
            .mapValues { (_, value) -> value.toLong() },
        HERO_MAX_HEALTH to snapshot.heroMaxHealth.mapKeys { it.key.value }
            .mapValues { (_, value) -> value.toLong() },
        HERO_STATUSES to HeroStatusCodec.toStatuses(snapshot.heroStatuses),
        PASSIVES to HeroStatusCodec.toPassives(snapshot.passives),
        MANAS to snapshot.manas.mapKeys { it.key.value }
            .mapValues { (_, value) -> value.toLong() },
        BASE_MAX_MANA to snapshot.baseMaxMana.toLong(),
        PLAYED_CARD_LAST_TURN to snapshot.playedCardLastTurn.mapKeys { it.key.value },
        CARD_OF to snapshot.cardOf.mapKeys { it.key.value }
            .mapValues { (_, cardId) -> cardId.value },
        WINNER to snapshot.winner?.value,
        LOG to snapshot.log,
    )
}
