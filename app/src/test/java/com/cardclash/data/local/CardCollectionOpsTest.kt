package com.cardclash.data.local

import com.cardclash.data.local.logic.CardCollectionOps
import com.cardclash.domain.progression.ProgressionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM (JUnit 4) de la logica PURA de anadir/eliminar copias validando
 * contra [ProgressionRules.maxCopiesAllowed]. Es la funcion extractada del
 * repositorio ([com.cardclash.data.PlayerProgressRepository]) que no depende
 * del runtime Room.
 *
 * Limites por tramo: T1(1-40)=2, T2(41-50)=3, T3(51-60)=4, T4(61-70)=5,
 * T5(71-80)=5 (tope contractual).
 */
class CardCollectionOpsTest {

    private val rules = ProgressionRules()

    // -----------------------------------------------------------------
    // canAddCopy
    // -----------------------------------------------------------------

    @Test
    fun canAddCopy_respetaLimiteDeCadaTramo() {
        // T1: limite 2.
        assertTrue(CardCollectionOps.canAddCopy(1, 40, rules))
        assertFalse(CardCollectionOps.canAddCopy(2, 40, rules))
        // T2: limite 3.
        assertTrue(CardCollectionOps.canAddCopy(2, 41, rules))
        assertFalse(CardCollectionOps.canAddCopy(3, 50, rules))
        // T3: limite 4.
        assertTrue(CardCollectionOps.canAddCopy(3, 51, rules))
        assertFalse(CardCollectionOps.canAddCopy(4, 60, rules))
        // T4/T5: limite 5 (tope contractual).
        assertTrue(CardCollectionOps.canAddCopy(4, 61, rules))
        assertFalse(CardCollectionOps.canAddCopy(5, 80, rules))
    }

    // -----------------------------------------------------------------
    // nextCountAfterAdd
    // -----------------------------------------------------------------

    @Test
    fun nextCountAfterAdd_dentroDelLimite_devuelveNuevoConteo() {
        assertEquals(
            CardCollectionOps.AddCopyOutcome.Added(2),
            CardCollectionOps.nextCountAfterAdd(1, 1, rules),
        )
        val t2 = CardCollectionOps.nextCountAfterAdd(2, 45, rules)
        assertEquals(CardCollectionOps.AddCopyOutcome.Added(3), t2)
        assertEquals(3, (t2 as CardCollectionOps.AddCopyOutcome.Added).newCount)
    }

    @Test
    fun nextCountAfterAdd_enElLimite_devuelveLimitReached() {
        assertEquals(
            CardCollectionOps.AddCopyOutcome.LimitReached(2, 2),
            CardCollectionOps.nextCountAfterAdd(2, 40, rules),
        )
        // Sube el nivel (41, T2, limite 3) y ya se puede anadir: Added(3).
        assertEquals(
            CardCollectionOps.AddCopyOutcome.Added(3),
            CardCollectionOps.nextCountAfterAdd(2, 41, rules),
        )
        // Tope contractual a nivel 80: 5 copias, no se anade mas.
        assertEquals(
            CardCollectionOps.AddCopyOutcome.LimitReached(5, 5),
            CardCollectionOps.nextCountAfterAdd(5, 80, rules),
        )
    }

    @Test
    fun nextCountAfterAdd_usaElNivelDerivadoNoElConteo() {
        // Mismas copias, distinto nivel: el limite cambia con el nivel.
        assertEquals(
            CardCollectionOps.AddCopyOutcome.LimitReached(2, 2),
            CardCollectionOps.nextCountAfterAdd(2, 40, rules),
        )
        assertEquals(
            CardCollectionOps.AddCopyOutcome.Added(3),
            CardCollectionOps.nextCountAfterAdd(2, 41, rules),
        )
    }

    // -----------------------------------------------------------------
    // nextCountAfterRemove
    // -----------------------------------------------------------------

    @Test
    fun nextCountAfterRemove_conCopias_devuelveConteoDecrementado() {
        assertEquals(
            CardCollectionOps.RemoveCopyOutcome.Removed(0),
            CardCollectionOps.nextCountAfterRemove(1),
        )
        assertEquals(
            CardCollectionOps.RemoveCopyOutcome.Removed(2),
            CardCollectionOps.nextCountAfterRemove(3),
        )
    }

    @Test
    fun nextCountAfterRemove_sinCopias_devuelveNotOwned() {
        assertEquals(CardCollectionOps.RemoveCopyOutcome.NotOwned, CardCollectionOps.nextCountAfterRemove(0))
        // Conteos negativos (dato corrupto) se tratan como no poseida.
        assertEquals(CardCollectionOps.RemoveCopyOutcome.NotOwned, CardCollectionOps.nextCountAfterRemove(-5))
    }
}