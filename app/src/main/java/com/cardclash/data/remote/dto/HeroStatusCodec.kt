package com.cardclash.data.remote.dto

import com.cardclash.domain.model.ActiveStatus
import com.cardclash.domain.model.PassiveBonus
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.UnitStat
import com.cardclash.data.remote.dto.WireFields.PASSIVE_AMOUNT
import com.cardclash.data.remote.dto.WireFields.PASSIVE_STAT

/**
 * Serializacion del estado del avatar (heroe) y de sus modificadores pasivos.
 *
 * Reemplaza al antiguo codec de unidades/tableros (eliminados en la Fase 1 del
 * rediseno). Este codec cubre los campos nuevos del
 * [com.cardclash.domain.model.MatchSnapshot]:
 * - [heroHealth] / [heroMaxHealth]: `Map<PlayerId, Int>` (vida actual y máxima).
 * - [passives]: `Map<PlayerId, List<PassiveBonus>>` (modificadores permanentes).
 *
 * Los [ActiveStatus] sobre el avatar se serializan con [ActiveStatusCodec] y los
 * mapas `heroHealth`, `heroMaxHealth`, `heroStatuses` y `passives` los centraliza
 * [WireFields].
 */
internal object HeroStatusCodec {

    /** Serializa un [PassiveBonus] individual hacia su representacion wire. */
    fun toPassive(passive: PassiveBonus): Map<String, Any?> = mapOf(
        PASSIVE_STAT to passive.stat.name,
        PASSIVE_AMOUNT to passive.amount.toLong(),
    )

    /** Deserializa un [PassiveBonus] individual desde su wire. */
    fun fromPassive(map: Map<String, Any?>): PassiveBonus = PassiveBonus(
        stat = UnitStat.valueOf(WireRead.requireStr(map, PASSIVE_STAT)),
        amount = WireRead.int(map, PASSIVE_AMOUNT),
    )

    /**
     * Serializa el mapa de pasivas por jugador
     * (`Map<PlayerId, List<PassiveBonus>>`).
     */
    fun toPassives(passives: Map<PlayerId, List<PassiveBonus>>): Map<String, Any?> =
        passives.mapKeys { it.key.value }
            .mapValues { (_, bonuses) -> bonuses.map(::toPassive) }

    /** Deserializa el mapa de pasivas por jugador desde su wire. */
    fun fromPassives(map: Map<String, Any?>): Map<PlayerId, List<PassiveBonus>> =
        map.mapKeys { (playerKey, _) -> PlayerId(playerKey) }
            .mapValues { (_, list) ->
                (list as List<*>).map { fromPassive(it as Map<String, Any?>) }
            }

    /**
     * Serializa el mapa de estados del avatar por jugador
     * (`Map<PlayerId, List<ActiveStatus>>`).
     */
    fun toStatuses(statuses: Map<PlayerId, List<ActiveStatus>>): Map<String, Any?> =
        statuses.mapKeys { it.key.value }
            .mapValues { (_, list) -> list.map(ActiveStatusCodec::toWire) }

    /** Deserializa el mapa de estados del avatar por jugador desde su wire. */
    fun fromStatuses(map: Map<String, Any?>): Map<PlayerId, List<ActiveStatus>> =
        map.mapKeys { (playerKey, _) -> PlayerId(playerKey) }
            .mapValues { (_, list) ->
                (list as List<*>).map { ActiveStatusCodec.fromWire(it as Map<String, Any?>) }
            }
}
