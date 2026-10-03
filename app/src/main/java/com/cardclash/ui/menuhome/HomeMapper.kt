package com.cardclash.ui.menuhome

import com.cardclash.data.local.model.PlayerProgress
import com.cardclash.domain.progression.CardCollection
import com.cardclash.domain.progression.ProgressionRules
import com.cardclash.ui.common.ProgressUiMapper

/**
 * Reducer puro (Kotlin/JVM, sin framework) de [PlayerProgress] + [CardCollection]
 * a [HomeUiState]. Testeable con unit tests JVM sin tocar Room ni Compose.
 */
object HomeMapper {

    fun toUiState(
        progress: PlayerProgress,
        collection: CardCollection,
        rules: ProgressionRules = ProgressionRules(),
    ): HomeUiState {
        val totalCopies = collection.ownedCards().sumOf { collection.ownedCount(it) }
        return HomeUiState(
            isLoading = false,
            level = progress.level,
            tier = progress.tier,
            xpTotal = progress.xpTotal,
            xpIntoLevel = progress.xpIntoLevel,
            xpNeededForNextLevel = progress.xpNeededForNextLevel,
            xpProgressFraction = ProgressUiMapper.progressFraction(progress, rules),
            collectionSize = collection.ownedCards().size,
            totalCopies = totalCopies,
        )
    }
}