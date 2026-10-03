package com.cardclash.data.local.logic

import com.cardclash.domain.model.CardId
import com.cardclash.domain.progression.ProgressionRules

/**
 * Logica PURA de validacion y transformacion de copias de la coleccion.
 *
 * Es la parte testable de [com.cardclash.data.PlayerProgressRepository] que no
 * depende del runtime Room: dado el numero actual de copias y el nivel (ya
 * derivado del XP), decide si se puede anadir otra copia contra
 * [ProgressionRules.maxCopiesAllowed] o eliminar una existente, y calcula el
 * nuevo conteo. El repositorio aplica el resultado dentro de una transaccion.
 */
object CardCollectionOps {

    /** Resultado de intentar anadir una copia. */
    sealed interface AddCopyOutcome {
        /** Copia anadida; [newCount] es el total poseido tras anadir. */
        data class Added(val newCount: Int) : AddCopyOutcome

        /** El limite del nivel no permite otra copia. */
        data class LimitReached(val currentCount: Int, val maxAllowed: Int) : AddCopyOutcome

        /** La carta no existe en el catalogo (no se puede poseer). */
        data class UnknownCard(val cardId: CardId) : AddCopyOutcome
    }

    /** Resultado de intentar eliminar una copia. */
    sealed interface RemoveCopyOutcome {
        /** Copia eliminada; [newCount] es el total restante (0 borra la fila). */
        data class Removed(val newCount: Int) : RemoveCopyOutcome

        /** No se poseia ninguna copia de la carta. */
        data object NotOwned : RemoveCopyOutcome
    }

    /** true si con [currentCopies] copias a nivel [level] aun se puede anadir. */
    fun canAddCopy(currentCopies: Int, level: Int, rules: ProgressionRules): Boolean =
        currentCopies < rules.maxCopiesAllowed(level)

    /** Decide y calcula el nuevo conteo al anadir una copia a nivel [level]. */
    fun nextCountAfterAdd(currentCopies: Int, level: Int, rules: ProgressionRules): AddCopyOutcome {
        val maxAllowed = rules.maxCopiesAllowed(level)
        return if (currentCopies < maxAllowed) {
            AddCopyOutcome.Added(currentCopies + 1)
        } else {
            AddCopyOutcome.LimitReached(currentCopies, maxAllowed)
        }
    }

    /** Decide y calcula el nuevo conteo al eliminar una copia (0 = ya no se posee). */
    fun nextCountAfterRemove(currentCopies: Int): RemoveCopyOutcome =
        if (currentCopies <= 0) {
            RemoveCopyOutcome.NotOwned
        } else {
            RemoveCopyOutcome.Removed(currentCopies - 1)
        }
}