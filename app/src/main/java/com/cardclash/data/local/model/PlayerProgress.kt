package com.cardclash.data.local.model

import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.progression.Tier

/**
 * Progresion DERIVADA del XP total persistido (valor inmutable, Kotlin puro).
 *
 * [level], [tier], [xpIntoLevel], [xpNeededForNextLevel] y [maxCopiesAllowed]
 * se derivan del XP total con
 * [com.cardclash.domain.progression.LevelCurve] /
 * [com.cardclash.domain.progression.ProgressionRules]; nunca se persisten como
 * estado para no desincronizarse con [xpTotal] (fuente de verdad).
 */
data class PlayerProgress(
    val playerId: PlayerId,
    val xpTotal: Int,
    val level: Int,
    val tier: Tier,
    /** XP sobrante dentro del nivel actual (0..necesaria-1). */
    val xpIntoLevel: Int,
    /** XP total necesaria para ALCANZAR el siguiente nivel, o null en nivel maximo. */
    val xpNeededForNextLevel: Int?,
    /** Limite de copias permitido para el nivel derivado (tope 5 a nivel 80). */
    val maxCopiesAllowed: Int,
    val updatedAt: Long,
)