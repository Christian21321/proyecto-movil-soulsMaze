package com.cardclash.ui.menuhome

import com.cardclash.domain.progression.Tier

/**
 * Estado UI del menu principal (UDF). [isLoading] es true mientras se carga el
 * progreso; el resto de campos resumen el progreso derivado del XP total.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val level: Int = 1,
    val tier: Tier = Tier.T1,
    val xpTotal: Int = 0,
    val xpIntoLevel: Int = 0,
    val xpNeededForNextLevel: Int? = null,
    val xpProgressFraction: Float = 0f,
    /** Cartas DISTINTAS poseidas (con al menos 1 copia). */
    val collectionSize: Int = 0,
    /** Copias totales acumuladas en la coleccion. */
    val totalCopies: Int = 0,
)