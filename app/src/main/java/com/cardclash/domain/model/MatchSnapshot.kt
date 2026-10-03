package com.cardclash.domain.model

/**
 * Instantánea inmutable de una partida de CardClash en un punto concreto del
 * tiempo.
 *
 * El motor de combate es FUNCIONAL: cada método (`applyAction`, etc.) recibe un
 * [MatchSnapshot] y devuelve una NUEVA copia (reconstruida mediante [copy])
 * sin mutar la de entrada. Solo el motor crea/modifica estado.
 *
 * # Estructura (tras el rediseño de Fase 1)
 *
 * Ya NO existen tableros de unidades: el juego es de cartas hechizo/efecto sobre
 * el AVATAR de cada jugador.
 * - [phase]: fase de la partida (PREPARING, PLAYING, FINISHED).
 * - [turn]: numero de turno actual (1-based).
 * - [currentPlayer]: jugador cuyo turno es.
 * - [hands]: mapa de cartas en mano por jugador (lista de [InstanceId]).
 * - [decks]: mapas de mazos por jugador; el mazo es CIRCULAR con re-barajado.
 * - [heroHealth] / [heroMaxHealth]: vida actual y máxima del avatar por jugador.
 * - [heroStatuses]: estados activos (BURN/POISON/BLEED/FROST) sobre cada avatar.
 * - [passives]: modificadores pasivos permanentes vinculados a cada avatar.
 * - [manas]: mana actual de cada jugador.
 * - [players]: orden y metadatos de los jugadores.
 * - [playedCardLastTurn]: mapa que guarda, por jugador, si jugó una carta en su
 *   turno anterior (afecta al robo del turno siguiente).
 *
 * Nota sobre el mana: el "tope efectivo" de cada jugador es [baseMaxMana] (10)
 * más la suma de las pasivas MAX_MANA vinculadas a su avatar ([passives]). El
 * motor rellena el mana hasta ese tope efectivo al iniciar el turno.
 */
data class MatchSnapshot(
    val matchId: MatchId,
    val phase: Phase,
    val turn: Int,
    val currentPlayer: PlayerId,
    val players: List<PlayerId>,
    val hands: Map<PlayerId, List<InstanceId>>,
    val decks: Map<PlayerId, List<InstanceId>>,
    /** Pila (mazo) ORIGINAL completo de cada jugador: fuente del re-barajado. */
    val fullDecks: Map<PlayerId, List<InstanceId>> = emptyMap(),
    /** Vida actual del avatar de cada jugador. */
    val heroHealth: Map<PlayerId, Int>,
    /** Vida máxima del avatar de cada jugador. */
    val heroMaxHealth: Map<PlayerId, Int>,
    /** Estados activos sobre el avatar de cada jugador. */
    val heroStatuses: Map<PlayerId, List<ActiveStatus>> = emptyMap(),
    /** Modificadores pasivos permanentes vinculados al avatar de cada jugador. */
    val passives: Map<PlayerId, List<PassiveBonus>> = emptyMap(),
    val manas: Map<PlayerId, Int>,
    val baseMaxMana: Int = 10,
    val playedCardLastTurn: Map<PlayerId, Boolean> = emptyMap(),
    /** Mapa de resolución instancia -> definición de carta. */
    val cardOf: Map<InstanceId, CardId> = emptyMap(),
    val winner: PlayerId? = null,
    val log: List<String> = emptyList(),
) {
    /** Fases del flujo de partida. */
    enum class Phase {
        /** Preparacion antes del primer turno de juego. */
        PREPARING,

        /** Partida en curso. */
        PLAYING,

        /** Partida terminada con [winner] resuelto. */
        FINISHED,
    }

    /**
     * Vida máxima del avatar según su nivel: `25 + 5 * nivel` (nivel 1 -> 30).
     * Es la fórmula autorizada del rediseño de Fase 1.
     */
    companion object {
        /** Nivel de avatar por defecto cuando no se provee. */
        const val DEFAULT_HERO_LEVEL: Int = 1

        /** Vida máxima del avatar para un [level] dado: `25 + 5 * level`. */
        fun heroMaxHealthForLevel(level: Int): Int {
            require(level >= 1) { "El nivel del avatar debe ser >= 1" }
            return 25 + 5 * level
        }
    }

    /** Cartas en mano de un jugador. */
    fun handOf(player: PlayerId): List<InstanceId> = hands[player].orEmpty()

    /** Mazo (cola circular) de un jugador. */
    fun deckOf(player: PlayerId): List<InstanceId> = decks[player].orEmpty()

    /** Vida actual del avatar de un jugador. */
    fun heroHealthOf(player: PlayerId): Int = heroHealth[player] ?: 0

    /** Vida máxima del avatar de un jugador. */
    fun heroMaxHealthOf(player: PlayerId): Int = heroMaxHealth[player] ?: 0

    /** Estados activos sobre el avatar de un jugador. */
    fun heroStatusesOf(player: PlayerId): List<ActiveStatus> = heroStatuses[player].orEmpty()

    /** Modificadores pasivos vinculados al avatar de un jugador. */
    fun passivesOf(player: PlayerId): List<PassiveBonus> = passives[player].orEmpty()

    /** Mana actual de un jugador. */
    fun manaOf(player: PlayerId): Int = manas[player] ?: 0

    /** Indica si el jugador jugó una carta en su turno anterior. */
    fun playedLastTurn(player: PlayerId): Boolean = playedCardLastTurn[player] ?: false

    /** Devuelve el jugador que sigue a [after] en el orden cíclico. */
    fun nextPlayer(after: PlayerId): PlayerId {
        val idx = players.indexOf(after)
        return players[(idx + 1) % players.size]
    }

    /**
     * Cartas disponibles para robar (hasta [target]). El mazo es CIRCULAR: si la
     * cola actual se agota, se re-baraja la pila ORIGINAL ([fullDecks]) con
     * [reshuffleImpl] y se reanuda la entrega.
     *
     * El re-barajado excluye las instancias que ya están en la mano y las que se
     * acaban de robar en esta misma llamada, de modo que una instancia nunca
     * aparece dos veces en la mano.
     *
     * Devuelve el par (cartas robadas, cola restante). Nunca lanza: si incluso el
     * mazo original está vacío, simplemente no aporta más cartas.
     */
    fun drawCards(
        player: PlayerId,
        target: Int,
        reshuffleImpl: (List<InstanceId>) -> List<InstanceId>,
    ): Pair<List<InstanceId>, List<InstanceId>> {
        var queue = deckOf(player)
        val drawn = mutableListOf<InstanceId>()
        val inHand = handOf(player).toSet()
        var guard = 0
        val safeGuard = 10_000
        while (drawn.size < target && guard < safeGuard) {
            guard++
            if (queue.isEmpty()) {
                // Re-baraja la pila ORIGINAL (fuente circular), nunca la cola agotada.
                // Excluye lo que ya está en mano y lo robado en este mismo robo.
                val source = fullDecks[player].orEmpty().filterNot { it in inHand || it in drawn }
                if (source.isEmpty()) break
                queue = reshuffleImpl(source)
            }
            if (queue.isEmpty()) break
            drawn += queue.first()
            queue = queue.drop(1)
        }
        return drawn to queue
    }

    /**
     * Tope efectivo de mana de un jugador: [baseMaxMana] más la suma de las
     * pasivas MAX_MANA vinculadas a su avatar ([passives]). Es el valor al que se
     * rellena el mana al iniciar (o continuar) el turno salvo que esté congelado.
     */
    fun effectiveMaxMana(player: PlayerId): Int {
        val passiveBonus = passivesOf(player).sumOf { bonus ->
            if (bonus.stat == UnitStat.MAX_MANA) bonus.amount else 0
        }
        return baseMaxMana + passiveBonus
    }

    /** Devuelve true si [player] es el jugador cuyo turno es. */
    fun isCurrent(player: PlayerId): Boolean = player == currentPlayer

    /** true si la partida ha terminado. */
    val isFinished: Boolean get() = phase == Phase.FINISHED

    /** Adjunta un mensaje de log manteniendo inmutabilidad. */
    fun withLog(message: String): MatchSnapshot =
        copy(log = log + message)

    /**
     * Rellena el mana del jugador. Respeta el tope [effectiveMaxMana].
     * Devuelve un nuevo snapshot (inmutable).
     */
    fun fillMana(player: PlayerId): MatchSnapshot {
        val target = effectiveMaxMana(player)
        val newManas = manas + (player to target)
        return copy(manas = newManas, log = log + "Mana de $player rellenado a $target.")
    }
}
