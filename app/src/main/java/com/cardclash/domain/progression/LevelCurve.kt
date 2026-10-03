package com.cardclash.domain.progression

/**
 * Curva de niveles de CardClash.
 *
 * Define cuanta XP se requiere para avanzar de un nivel al siguiente y permite
 * derivar el NIVEL y la XP dentro del nivel a partir de la XP total acumulada.
 * La curva es INVERTIBLE: dado XP total se calcula nivel y XP-sobrante.
 *
 * ## Curva documentada (progresiva por tramo)
 *
 * La XP necesaria por nivel crece segun el tramo en el que se encuentre el
 * nivel de ORIGEN (la XP que cuesta salir del tramo):
 *
 * | Tramo      | Niveles   | XP por nivel |
 * |------------|-----------|--------------|
 * | T1         | 1-40      | 100          |
 * | T2         | 41-50     | 150          |
 * | T3         | 51-60     | 200          |
 * | T4         | 61-70     | 300          |
 * | T5         | 71-80     | 400          |
 *
 * `xpForLevel(level)` devuelve la XP TOTAL necesaria para ALCANZAR [level],
 * como suma de la XP por nivel desde el nivel 1. Para el nivel 1 la XP
 * requerida es 0 (se empieza ya en nivel 1).
 *
 * La XP global no tiene techo; [MAX_LEVEL] (80) solo topa el nivel derivado.
 * Mas alla de 80 la XP sigue acumulandose pero [levelForXp] devuelve 80.
 */
class LevelCurve {

    companion object {
        /** Nivel maximo alcanzable (tope de la curva). */
        const val MAX_LEVEL = 80
    }

    /**
     * XP necesaria para pasar del nivel [level] al siguiente (o acumulada
     * dentro del tramo al que pertenece [level]). [level] debe estar en 1..80.
     */
    fun xpPerLevel(level: Int): Int = when (Tier.forLevel(level)) {
        Tier.T1 -> 100
        Tier.T2 -> 150
        Tier.T3 -> 200
        Tier.T4 -> 300
        Tier.T5 -> 400
    }

    /**
     * XP TOTAL acumulada necesaria para ALCANZAR el nivel [level]
     * (suma de [xpPerLevel] desde el nivel 1). [level] en 1..80; para 1 devuelve 0.
     */
    fun xpForLevel(level: Int): Int {
        require(level in 1..MAX_LEVEL) { "Nivel $level fuera del rango 1..$MAX_LEVEL" }
        if (level == 1) return 0
        return (1 until level).sumOf { xpPerLevel(it) }
    }

    /**
     * Deriva el nivel a partir de la XP total acumulada.
     * Devuelve el mayor nivel alcanzable sin superar [xp] (topado a [MAX_LEVEL]).
     * La XP total se acumula sin techo: si [xp] excede la XP del nivel maximo,
     * devuelve [MAX_LEVEL].
     */
    fun levelForXp(xp: Int): Int {
        val total = xp.coerceAtLeast(0)
        var level = 1
        while (level < MAX_LEVEL && total >= xpForLevel(level + 1)) {
            level++
        }
        return level
    }

    /**
     * XP sobrante dentro del nivel actual (0..xpPerLevel(level)-1).
     * Dado XP total, devuelve la XP acumulada en [levelForXp] desde que se
     * alcanzo ese nivel.
     */
    fun xpIntoLevel(xp: Int): Int {
        val total = xp.coerceAtLeast(0)
        return total - xpForLevel(levelForXp(total))
    }
}
