package com.cardclash.domain.model

/**
 * Rareza de una carta de CardClash.
 *
 * La rareza es un dato de PROGRESION/COLECCION (no de combate): el motor de
 * combate depende de [Card.cost], [Card.attack], etc., nunca de la rareza. Se
 * usa para derivar el coste de mana de una carta y como eje de la progression
 * de coleccion.
 *
 * Cada rareza declara el coste de mana [manaCost] que define la base economica
 * de la carta dentro de su rareza:
 * - [COMMON]: 3 de mana.
 * - [SR]: 4 de mana.
 * - [SSR]: 5 de mana.
 */
enum class Rarity(val manaCost: Int) {
    COMMON(3),
    SR(4),
    SSR(5),
}
