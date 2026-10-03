package com.cardclash.domain.progression

import com.cardclash.domain.model.Card

/**
 * Reglas de progresion y coleccion de CardClash.
 *
 * Dado el XP total de un jugador y las copias que posee de una carta, deriva
 * el nivel, el tramo, el limite de copias permitido por tramo y aplica el
 * desbloqueo escalonado (consumo de 1 copia por tramo T2-T5).
 *
 * ## Limite de copias por tramo (documentado)
 *
 * El jugador DESBLOQUEA el aumento del limite de copias consumiendo 1 copia de
 * la carta por tramo T2-T5. El limite interno crece +1 por tramo sobre una base
 * de [BASE_COPY_LIMIT] (2), pero queda CAPEADO por el tope contractual
 * [CONTRACTUAL_MAX_COPIES] (5), que es "5 copias max para nivel 80".
 *
 * | Tramo | Niveles | Copias consumidas | Limite interno | Limite maximo efectivo |
 * |-------|---------|-------------------|----------------|-------------------------|
 * | T1    | 1-40    | 0                 | 2              | 2                       |
 * | T2    | 41-50   | 1                 | 3              | 3                       |
 * | T3    | 51-60   | 2                 | 4              | 4                       |
 * | T4    | 61-70   | 3                 | 5              | 5                       |
 * | T5    | 71-80   | 4                 | 6              | 5 (tope contractual)    |
 *
 * La tabla es coherente con el modelo del usuario: se consume 1 copia por cada
 * tramo T2-T5 (4 desbloqueos) y el tope por nivel 80 es 5. El limite interno
 * del T5 llegaria a 6 pero el tope contractual lo mantiene en 5.
 */
class ProgressionRules(
    val curve: LevelCurve = LevelCurve(),
) {

    companion object {
        /** Limite base de copias en el tramo inicial (T1). */
        const val BASE_COPY_LIMIT = 2

        /** Tope contractual maximo de copias poseidas ("5 para nivel 80"). */
        const val CONTRACTUAL_MAX_COPIES = 5
    }

    // ------------------------------------------------------------------
    // Nivel / tramo
    // ------------------------------------------------------------------

    /** Nivel derivado del XP total (delegado a [LevelCurve]). */
    fun levelForXp(xp: Int): Int = curve.levelForXp(xp)

    /** Tramo al que pertenece [level]. */
    fun tierForLevel(level: Int): Tier = Tier.forLevel(level)

    /** Tramo al que pertenece el nivel derivado de [xp]. */
    fun tierForXp(xp: Int): Tier = tierForLevel(levelForXp(xp))

    // ------------------------------------------------------------------
    // Limite de copias y desbloqueos
    // ------------------------------------------------------------------

    /**
     * Copias que el jugador ha CONSUMIDO por desbloqueos de tramo hasta [level].
     * Es 0 en T1 y crece en 1 por tramo (T2=1, T3=2, T4=3, T5=4).
     */
    fun copiesConsumedForTier(level: Int): Int = tierForLevel(level).ordinal

    /**
     * Limite maximo de copias permitido para una carta a [level].
     * Es el limite interno del tramo capeado por [CONTRACTUAL_MAX_COPIES].
     */
    fun maxCopiesAllowed(level: Int): Int {
        val raw = BASE_COPY_LIMIT + copiesConsumedForTier(level)
        return raw.coerceAtMost(CONTRACTUAL_MAX_COPIES)
    }

    /**
     * true si el jugador puede anadir una copia mas de [card] a nivel [level]
     * dado que ya posee [currentCopies] copias. No depende de la rareza, solo
     * del limite derivado del nivel.
     */
    fun canAddCopy(card: Card, currentCopies: Int, level: Int): Boolean =
        currentCopies < maxCopiesAllowed(level)

    // ------------------------------------------------------------------
    // Otorgar XP
    // ------------------------------------------------------------------

    /** NUEVO XP total tras otorgar [source] al XP actual [actualXp]. */
    fun grantXp(actualXp: Int, source: XpSource): Int =
        actualXp + source.rewardXp

    /**
     * NUEVO XP total tras otorgar [amount] XP de la tienda (capa de tienda).
     * [amount] debe ser 0..[XpSource.SHOP.rewardXp] (<=2).
     */
    fun grantShopXp(actualXp: Int, amount: Int): Int {
        require(amount in 0..XpSource.SHOP.rewardXp) {
            "La XP de tienda debe estar entre 0 y ${XpSource.SHOP.rewardXp}"
        }
        return actualXp + amount
    }
}
