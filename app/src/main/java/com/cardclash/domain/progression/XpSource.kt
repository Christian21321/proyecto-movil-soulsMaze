package com.cardclash.domain.progression

/**
 * Fuentes de experiencia (XP) global en CardClash.
 *
 * La XP se acumula globalmente sin techo ([ProgressionRules.MAX_LEVEL] limita
 * el NIVEL derivado, no la XP total). Cada fuente otorga una cantidad fija:
 * - [VICTORY]: +5 XP por victoria.
 * - [DEFEAT]: +2 XP por derrota.
 * - [SHOP]: hasta +2 XP por compra en la tienda (la capa de tienda decide la
 *   cantidad real entre 0 y 2; [rewardXp] es el tamano maximo/de referencia).
 *
 * [SHOP.rewardXp] se trata como el MAXIMO otorgable; usar
 * [ProgressionRules.grantXp] con [SHOP] concede ese tope, y la capa de tienda
 * puede otorgar menos (`grantXp(xp, XpSource.SHOP, cantidad)`).
 */
enum class XpSource(val rewardXp: Int) {
    VICTORY(5),
    DEFEAT(2),
    SHOP(2),
}
