package com.cardclash.domain.model

/**
 * Criterio escalar de comparacion usado para seleccionar el valor efectivo de
 * una estadistica (p.ej. el valor de ataque tras aplicar pasivas).
 *
 * Ayuda a implementar "el mayor / el menor" de los valores aportados por las
 * cartas del tablero al resolver pasivas como MAX_MANA.
 */
sealed class ValueSpec {
    /** Utiliza el mayor de los valores considerados. */
    data object Max : ValueSpec()

    /** Utiliza el menor de los valores considerados. */
    data object Min : ValueSpec()

    /** Utiliza directamente el valor de referencia proporcionado. */
    data class Fixed(val amount: Int) : ValueSpec() {
        init {
            require(amount >= 0) { "Un Fixed no puede ser negativo" }
        }
    }
}
