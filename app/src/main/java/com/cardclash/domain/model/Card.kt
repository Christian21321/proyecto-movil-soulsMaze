package com.cardclash.domain.model

/**
 * Definicion inmutable de una carta del catalogo de CardClash.
 *
 * Incluye tanto los atributos estaticos (coste, estadisticas base) como el
 * [CardEffect] que produce al ser jugada y un posible efecto pasivo permanente
 * [passiveStat] / [passiveAmount].
 *
 * - [effect]: efecto que se dispara al JUGAR la carta (ataque, curacion, robo,
 *   imposicion de estado), siempre apuntando al avatar de un jugador.
 * - [passiveStat] y [passiveAmount]: si no son nulos, la carta aporta un
 *   modificador pasivo permanente vinculado al avatar del jugador que la juega
 *   (p.ej. +1 MAX_MANA por pasiva, sumado en [MatchSnapshot.passives]).
 * - [isPassive]: indica que la carta es PASIVA y, al jugarse, se vincula al
 *   avatar sin disparar [effect] (su unico proposito es su modificador).
 *
 * [attack] y [maxHealth] se conservan por compatibilidad con el catalogo y la
 * UI, pero tras el rediseño de Fase 1 ya NO representan una unidad en tablero
 * (el juego no tiene unidades): el combate se decide sobre la vida del avatar.
 */
data class Card(
    val id: CardId,
    val name: String,
    val cost: Int,
    val attack: Int,
    val maxHealth: Int,
    val effect: CardEffect = CardEffect.None,
    val passiveStat: UnitStat? = null,
    val passiveAmount: Int = 0,
    val isPassive: Boolean = false,
) {
    init {
        require(cost >= 0) { "El coste de una carta no puede ser negativo" }
        require(attack >= 0) { "El ataque base no puede ser negativo" }
        require(maxHealth > 0) { "La salud maxima debe ser positiva" }
        require(!isPassive || (passiveStat != null && passiveAmount != 0)) {
            "Una carta pasiva debe declarar passiveStat y passiveAmount"
        }
    }
}
