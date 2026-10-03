package com.cardclash.domain.progression

/**
 * Tramos de nivel de la progresion de CardClash.
 *
 * Los niveles 1-80 se agrupan en 5 tramos (T1..T5). Cada tramo define un rango
 * de niveles [minLevel]..[maxLevel] y un tope de copias desbloqueable.
 *
 * - [T1]: niveles 1-40. Tramo base.
 * - [T2]: niveles 41-50.
 * - [T3]: niveles 51-60.
 * - [T4]: niveles 61-70.
 * - [T5]: niveles 71-80. Tramo maximo.
 */
enum class Tier(val minLevel: Int, val maxLevel: Int) {
    T1(1, 40),
    T2(41, 50),
    T3(51, 60),
    T4(61, 70),
    T5(71, 80);

    /** true si [level] cae dentro del rango de este tramo. */
    fun contains(level: Int): Boolean = level in minLevel..maxLevel

    companion object {
        /**
         * Devuelve el tramo al que pertenece [level].
         * Exige un nivel valido (1..80); niveles fuera de rango lanzan
         * [IllegalArgumentException].
         */
        fun forLevel(level: Int): Tier {
            require(level in 1..80) { "Nivel $level fuera del rango 1..80" }
            return entries.first { it.contains(level) }
        }
    }
}
