package com.cardclash.domain.model

/**
 * Registro central de "interacciones" entre estados.
 *
 * Es un punto unico y testeable para definir como se relacionan los estados
 * entre si, en concreto la neutralizacion de un estado por otro.
 *
 * Regla implementada: FROST NEUTRALIZA BURN. Cuando a una unidad se le aplica
 * FROST, cualquier BURN activo sobre ella queda suprimido (se elimina). Esta
 * regla es la que permite que la congelacion "apague un incendio".
 *
 * Se mantiene como objeto independiente del motor para que pueda evolucionar
 * sin tocar la logica de combate y para que su comportamiento sea verificable
 * aisladamente.
 */
object StatusInteractionRegistry {

    /** Conjunto (fuente -> diana) de neutralizaciones entre estados. */
    private val neutralizations: Map<StatusType, Set<StatusType>> =
        mapOf(
            // FROST (fuente) neutraliza BURN (diana).
            StatusType.FROST to setOf(StatusType.BURN),
        )

    /**
     * Devuelve true si [source] neutraliza [target].
     *
     * Por ejemplo: `neutralizes(FROST, BURN) == true`.
     */
    fun neutralizes(source: StatusType, target: StatusType): Boolean =
        neutralizations[source]?.contains(target) == true
}
