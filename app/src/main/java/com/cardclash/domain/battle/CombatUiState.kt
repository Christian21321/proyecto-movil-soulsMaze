package com.cardclash.domain.battle

import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.Rarity
import com.cardclash.domain.model.StatusType

/**
 * Modelo de UI puro e inmutable para la pantalla de combate.
 *
 * Representa la partida desde la PERSPECTIVA de un jugador ("yo") frente a su
 * rival, con TODOS los datos derivados y resueltos (nombres, rareza, etiquetas)
 * para que la capa Compose pueda renderizarla SIN conocer el dominio de combate
 * completo ni el catálogo.
 *
 * Tras el rediseño de Fase 1 (sin tableros de unidades), la UI expone la vida de
 * ambos AVATARES: [myHeroHealth]/[myHeroMaxHealth] y [opponentHeroHealth]/
 * [opponentHeroMaxHealth]. La vida del rival es SIEMPRE visible (no es secreto),
 * y por ello se expone sin condiciones.
 *
 * Se produce exclusivamente a partir de un [MatchSnapshot] (o de una proyección
 * de cliente) mediante [CombatReducer]. Es un `data class` inmutable, por lo que
 * los cambios de estado generan nuevas instancias vía `copy`.
 */
data class CombatUiState(
    /** Identificador de la partida. */
    val matchId: MatchId,
    /** Fase de la partida. */
    val phase: MatchSnapshot.Phase,
    /** Número de turno actual (1-based). */
    val turn: Int,
    /** Jugador cuyo turno es ahora mismo. */
    val currentPlayer: PlayerId,
    /** Identidad del jugador local ("yo"). */
    val myId: PlayerId,
    /** true si [myId] tiene el turno. */
    val isMyTurn: Boolean,
    /** Mana actual del jugador local. */
    val myMana: Int,
    /** Tope efectivo de mana del jugador local (base + pasivas MAX_MANA). */
    val maxMana: Int,
    /** Cartas en mano del jugador local, resueltas para render. */
    val myHand: List<HandCardUi>,
    /** Vida del avatar del jugador local. */
    val myHeroHealth: Int,
    /** Vida máxima del avatar del jugador local. */
    val myHeroMaxHealth: Int,
    /** Vida del avatar del rival (siempre visible). */
    val opponentHeroHealth: Int,
    /** Vida máxima del avatar del rival (siempre visible). */
    val opponentHeroMaxHealth: Int,
    /** Número de cartas en mano del rival (SIN revelar cuáles son). */
    val opponentHandSize: Int,
    /** Mana actual del rival. */
    val opponentMana: Int,
    /** Mensajes de log (últimos [maxLogLines] de la fuente). */
    val log: List<String>,
    /** Ganador de la partida, o null si no ha terminado. */
    val winner: PlayerId?,
    /** Cartas restantes en el mazo local (null si no está disponible). */
    val myDeckSize: Int?,
    /** Cartas restantes en el mazo rival (null si no está disponible). */
    val opponentDeckSize: Int?,
) {
    /** true si la partida ha terminado. */
    val isFinished: Boolean get() = phase == MatchSnapshot.Phase.FINISHED

    /** true si el jugador local puede realizar acciones (su turno y en curso). */
    val canAct: Boolean get() = isMyTurn && phase == MatchSnapshot.Phase.PLAYING

    /** true si el jugador local ganó la partida. */
    val isVictory: Boolean get() = winner != null && winner == myId
}

/**
 * Carta en mano del jugador local, lista para render con todos sus atributos
 * resueltos a través del catálogo.
 */
data class HandCardUi(
    val instanceId: InstanceId,
    val cardId: CardId,
    val name: String,
    val cost: Int,
    val attack: Int,
    val maxHealth: Int,
    val rarity: Rarity?,
    val isPassive: Boolean,
    /** true si la carta puede jugarse en este instante (turno + fase + mana + no congelado). */
    val playable: Boolean,
)

/**
 * Etiqueta de un estado de avatar (p. ej. "FROST", "BURN") lista para render.
 *
 * Se mantiene como utilidad ligera para que la UI indique qué estados sufre el
 * avatar de cada jugador. La lista se deriva de [MatchSnapshot.heroStatusesOf].
 */
data class HeroStatusUi(
    val playerId: PlayerId,
    val statuses: List<StatusType>,
) {
    /** Etiquetas legibles de los estados activos. */
    val statusLabels: List<String> get() = statuses.map { it.name }

    /** true si el avatar está congelado (salta turno completo). */
    val frozen: Boolean get() = statuses.contains(StatusType.FROST)
}
