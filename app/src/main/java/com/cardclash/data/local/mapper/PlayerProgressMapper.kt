package com.cardclash.data.local.mapper

import com.cardclash.data.local.entity.PlayerProfileEntity
import com.cardclash.data.local.model.PlayerProgress
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.progression.LevelCurve
import com.cardclash.domain.progression.ProgressionRules
import com.cardclash.domain.progression.XpSource

/**
 * Mapeos puros entidad Room <-> progresion derivada del dominio.
 *
 * Kotlin PURO (sin runtime Room): la derivacion nivel/tramo/XP-sobrante se hace
 * con [LevelCurve]/[ProgressionRules] del dominio y se testea con unit tests
 * JVM ([com.cardclash.data.local.PlayerProgressMapperTest]).
 *
 * La regla de persistencia es: SOLO el XP total es estado persistido
 * ([PlayerProfileEntity.xpTotal]); nivel, tramo y limite de copias se derivan
 * en memoria en [deriveProgress] y nunca se guardan como columnas.
 */
object PlayerProgressMapper {

    /** Convierte identificador + XP total + timestamp a fila Room. */
    fun toEntity(playerId: PlayerId, xpTotal: Int, updatedAt: Long): PlayerProfileEntity =
        PlayerProfileEntity(playerId.value, xpTotal, updatedAt)

    /** Deriva la progresion completa desde la fila Room persistida. */
    fun toProgress(
        entity: PlayerProfileEntity,
        rules: ProgressionRules = ProgressionRules(),
    ): PlayerProgress = deriveProgress(entity.playerId, entity.xpTotal, entity.updatedAt, rules)

    /**
     * Deriva nivel, tramo, XP-sobrante y limite de copias desde el XP total.
     * Es la funcion pura que garantiza que la derivacion NUNCA se desincroniza
     * del XP acumulado (fuente de verdad).
     */
    fun deriveProgress(
        playerId: String,
        xpTotal: Int,
        updatedAt: Long,
        rules: ProgressionRules = ProgressionRules(),
    ): PlayerProgress {
        val level = rules.levelForXp(xpTotal)
        return PlayerProgress(
            playerId = PlayerId(playerId),
            xpTotal = xpTotal,
            level = level,
            tier = rules.tierForLevel(level),
            xpIntoLevel = rules.curve.xpIntoLevel(xpTotal),
            xpNeededForNextLevel = if (level < LevelCurve.MAX_LEVEL) {
                rules.curve.xpForLevel(level + 1)
            } else {
                null
            },
            maxCopiesAllowed = rules.maxCopiesAllowed(level),
            updatedAt = updatedAt,
        )
    }

    // ------------------------------------------------------------------
    // Otorgar XP (delegaciones puras a ProgressionRules para el repositorio)
    // ------------------------------------------------------------------

    /** Nuevo XP total tras una victoria (+5). */
    fun grantVictory(xpTotal: Int, rules: ProgressionRules = ProgressionRules()): Int =
        rules.grantXp(xpTotal, XpSource.VICTORY)

    /** Nuevo XP total tras una derrota (+2). */
    fun grantDefeat(xpTotal: Int, rules: ProgressionRules = ProgressionRules()): Int =
        rules.grantXp(xpTotal, XpSource.DEFEAT)

    /**
     * Nuevo XP total tras XP de tienda ([amount] en 0..2, validado por
     * [ProgressionRules.grantShopXp]; lanza [IllegalArgumentException] si no).
     */
    fun grantShop(xpTotal: Int, amount: Int, rules: ProgressionRules = ProgressionRules()): Int =
        rules.grantShopXp(xpTotal, amount)
}