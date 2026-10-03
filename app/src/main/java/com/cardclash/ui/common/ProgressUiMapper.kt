package com.cardclash.ui.common

import com.cardclash.data.local.model.PlayerProgress
import com.cardclash.domain.progression.ProgressionRules

/**
 * Mapeos UI puros compartidos entre pantallas (Kotlin/JVM, sin framework).
 *
 * [progressFraction] es la unica derivacion comun de la cabecera de progreso:
 * XP dentro del nivel dividida entre la XP necesaria para subir de nivel
 * ([ProgressionRules.curve]/[LevelCurve.xpPerLevel]). En nivel maximo la barra
 * se llena (1f) y [PlayerProgress.xpNeededForNextLevel] es null.
 */
object ProgressUiMapper {

    /** Fraccion 0..1 de avance dentro del nivel actual. */
    fun progressFraction(progress: PlayerProgress, rules: ProgressionRules): Float {
        if (progress.xpNeededForNextLevel == null) return 1f
        val perLevel = rules.curve.xpPerLevel(progress.level)
        if (perLevel <= 0) return 0f
        return (progress.xpIntoLevel.toFloat() / perLevel).coerceIn(0f, 1f)
    }
}