package com.cardclash.domain.engine

import com.cardclash.domain.model.ActiveStatus
import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardEffect
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchResult
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PassiveBonus
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.StatusDecay
import com.cardclash.domain.model.StatusInteractionRegistry
import com.cardclash.domain.model.StatusInstanceId
import com.cardclash.domain.model.StatusType
import com.cardclash.domain.model.WinReason
import com.cardclash.domain.repository.CardCatalog

/**
 * Motor de combate de CardClash: lógica FUNCIONAL e INMUTABLE.
 *
 * # Principios
 * - Ningún método muta el [MatchSnapshot] de entrada: cada acción devuelve una
 *   NUEVA copia reconstruida mediante `copy()`. Únicamente el motor crea/modifica
 *   estado.
 * - La aleatoriedad se inyecta por [DiceRoller]; el motor JAMÁS crea un `Random`
 *   interno. Con [KotlinRandomDiceRoller] en producción y [SeededDiceRoller]
 *   (determinista) en tests (para BLEED 1d3).
 * - Las reglas de juego están separadas en pasos bien localizados: inicio de
 *   turno, robo, relleno de mana, juego de carta, fin de turno, victoria.
 *
 * # Rediseño de Fase 1 (sin unidades, avatar con vida)
 * - Eliminado el tablero de unidades y el ataque entre unidades.
 * - Cada jugador tiene un AVATAR con vida actual y máxima (`25 + 5 * nivel`).
 * - Las cartas hechizo/efecto golpean directamente el avatar del rival:
 *   `Attack` daña el avatar rival, `Heal` cura el avatar objetivo (propio o
 *   rival), `ApplyStatus` impone el estado al avatar objetivo.
 * - Las pasivas (MAX_MANA, etc.) se vinculan al avatar ([MatchSnapshot.passives]).
 * - Condición de victoria: vida del avatar a 0 (gana el otro jugador,
 *   [WinReason.OPPONENT_DEFEATED]).
 *
 * # Reglas de robo (tope de mano = 4)
 * - Mano inicial por defecto = 3.
 * - Robo al iniciar el turno: se rellena hasta 4 en total. El robo se DETIENE en
 *   cuanto la mano llega a 4 (no se roban cartas extra hasta jugar una y bajar de
 *   4). Un robo por efecto [CardEffect.Draw] también respeta el tope de 4.
 *
 * # Reglas de estados
 * - FROST salta el turno completo (no roba ni rellena mana).
 * - FROST NEUTRALIZA BURN vía [StatusInteractionRegistry].
 * - Refresco de estados: al reaparecer, `turnsRemaining = durationTurns`.
 * - Ticks de estados sobre el avatar: BURN/POISON fijos, BLEED 1d3 con el roller.
 * - Mana relleno a [MatchSnapshot.effectiveMaxMana] (base 10 + pasivas MAX_MANA).
 * - Curación con tope en la vida máxima del avatar.
 */
class CombatEngine(
    /** Catálogo de definiciones de cartas (inyectado; permite invertir la D. ). */
    private val catalog: CardCatalog,
    /** Fuente de aleatoriedad (inyectable; ver [DiceRoller]). */
    private val diceRoller: DiceRoller,
    /** Constante: semilla por defecto para el re-barajado del mazo. */
    private val defaultDeckSeed: Long = 42L,
) {
    /** Tope máximo de cartas en mano (regla autorizada del rediseño). */
    companion object {
        const val MAX_HAND_SIZE: Int = 4
        const val DEFAULT_INITIAL_HAND_SIZE: Int = 3
    }

    // ---------------------------------------------------------------------
    // Acciones que el motor puede aplicar a un snapshot
    // ---------------------------------------------------------------------

    sealed interface CombatAction {
        /** Inicia el turno del jugador actual: refresco, mana y robo. */
        data object BeginTurn : CombatAction

        /** Juega la carta [instanceId], apuntando al avatar [targetPlayer] (opcional). */
        data class PlayCard(
            val instanceId: InstanceId,
            val targetPlayer: PlayerId? = null,
        ) : CombatAction

        /** Termina el turno del jugador actual: ticks de estados y avance. */
        data object EndTurn : CombatAction
    }

    /** Resultado de aplicar una acción. */
    data class EngineResult(
        val snapshot: MatchSnapshot,
        val result: MatchResult? = null,
    )

    /**
     * Aplica una [CombatAction] a [snapshot] y devuelve un nuevo resultado.
     * Si la partida llega a un fin, se devuelve el [MatchResult] correspondiente.
     */
    fun applyAction(snapshot: MatchSnapshot, action: CombatAction): EngineResult =
        when (action) {
            is CombatAction.BeginTurn -> beginTurn(snapshot)
            is CombatAction.PlayCard -> playCard(snapshot, action)
            is CombatAction.EndTurn -> endTurn(snapshot)
        }

    /**
     * Crea un snapshot inicial para una partida entre [players], robusto y
     * determinista: mano con las primeras cartas del mazo (sin azar), mana 0,
     * vida de cada avatar según su nivel (por defecto 1 -> 30), fase PLAYING y
     * turno 1 con el primer jugador como actual.
     *
     * @param heroLevelByPlayer nivel del avatar de cada jugador (opcional; por
     *   defecto [MatchSnapshot.DEFAULT_HERO_LEVEL] -> 30 de vida máxima).
     * @param initialHandSize tamaño inicial de la mano (por defecto 3).
     */
    fun newMatch(
        matchId: MatchId,
        players: List<PlayerId>,
        deckByPlayer: Map<PlayerId, List<InstanceId>>,
        heroLevelByPlayer: Map<PlayerId, Int> = emptyMap(),
        initialHandSize: Int = DEFAULT_INITIAL_HAND_SIZE,
    ): MatchSnapshot {
        require(players.size >= 2) { "Una partida requiere al menos 2 jugadores" }

        val heroMax = players.associateWith { player ->
            val level = heroLevelByPlayer[player] ?: MatchSnapshot.DEFAULT_HERO_LEVEL
            MatchSnapshot.heroMaxHealthForLevel(level)
        }

        val base = MatchSnapshot(
            matchId = matchId,
            phase = MatchSnapshot.Phase.PLAYING,
            turn = 1,
            currentPlayer = players.first(),
            players = players,
            hands = players.associateWith {
                deckByPlayer[it].orEmpty().take(initialHandSize)
            },
            decks = players.associateWith { player ->
                deckByPlayer[player].orEmpty().drop(initialHandSize)
            },
            fullDecks = players.associateWith { player ->
                deckByPlayer[player].orEmpty()
            },
            heroHealth = heroMax,
            heroMaxHealth = heroMax,
            heroStatuses = players.associateWith { emptyList<ActiveStatus>() },
            passives = players.associateWith { emptyList<PassiveBonus>() },
            manas = players.associateWith { 0 },
        )
        // No realizamos BeginTurn automático: el primer BeginTurn lo lanza el
        // flujo de la partida.
        return base
    }

    // ---------------------------------------------------------------------
    // Inicio de turno
    // ---------------------------------------------------------------------

    private fun beginTurn(snapshot: MatchSnapshot): EngineResult {
        val player = snapshot.currentPlayer
        // FROST: si el avatar está congelado, salta el turno COMPLETO (no roba
        // ni rellena mana).
        if (isPlayerFrosted(snapshot, player)) {
            return EngineResult(
                snapshot.withLog("$player congelado: salta su turno (sin robo ni mana)."),
            )
        }

        // 1) Refresco de estados del avatar: se reaplica duración (TIMED).
        val refreshed = refreshHeroStatuses(snapshot, player)

        // 2) Relleno de mana hasta el tope efectivo (base 10 + pasivas MAX_MANA).
        val withMana = refreshed.fillMana(player)

        // 3) Robo con tope de mano 4: se rellena hasta 4 en total. Si la mano ya
        //    tiene 4 (por no haber jugado cartas), no se roba nada (robo detenido).
        val currentHand = withMana.handOf(player)
        val toDraw = (MAX_HAND_SIZE - currentHand.size).coerceAtLeast(0)
        val (drawn, restQueue) = withMana.drawCards(
            player = player,
            target = toDraw,
            seed = defaultDeckSeed,
            reshuffleImpl = ::reshuffleDeck,
        )
        val newHand = currentHand + drawn
        val finalSnap = withMana.copy(
            hands = withMana.hands + (player to newHand),
            decks = withMana.decks + (player to restQueue),
            log = withMana.log + "$player roba ${drawn.size} carta(s) (tope de mano $MAX_HAND_SIZE).",
        )
        return EngineResult(finalSnap)
    }

    // ---------------------------------------------------------------------
    // Jugar carta
    // ---------------------------------------------------------------------

    private fun playCard(snapshot: MatchSnapshot, action: CombatAction.PlayCard): EngineResult {
        val player = snapshot.currentPlayer

        // --- Validaciones de PlayCard ---
        val validationError = validatePlay(snapshot, action, player)
        if (validationError != null) {
            return EngineResult(snapshot.withLog(validationError))
        }

        val card = cardOf(snapshot, player, action.instanceId)
            ?: return EngineResult(snapshot.withLog("Carta inexistente o sin definición."))
        val newCost = snapshot.manaOf(player) - card.cost

        // Quitar la carta de la mano y descontar el mana.
        var working = snapshot.copy(
            hands = snapshot.hands + (player to snapshot.handOf(player) - action.instanceId),
            manas = snapshot.manas + (player to newCost),
            playedCardLastTurn = snapshot.playedCardLastTurn + (player to true),
        )

        // --- PASSIVE: se vincula al avatar y aplica su modificador permanente ---
        if (card.isPassive) {
            working = addPassiveBonus(working, player, card)
                .withLog("$player juega la pasiva '${card.name}' (se vincula al avatar).")
            return EngineResult(working)
        }

        // --- Carta normal: resuelve su efecto sobre el avatar objetivo ---
        working = resolveEffect(working, player, card, action.targetPlayer)
        return EngineResult(working)
    }

    /** Devuelve un mensaje de error si la jugada no es válida, o null si lo es. */
    private fun validatePlay(
        snapshot: MatchSnapshot,
        action: CombatAction.PlayCard,
        player: PlayerId,
    ): String? {
        if (snapshot.phase != MatchSnapshot.Phase.PLAYING) {
            return "No se puede jugar: la partida no está en curso."
        }
        if (!snapshot.isCurrent(player)) {
            return "No es el turno de $player."
        }
        if (snapshot.handOf(player).none { it == action.instanceId }) {
            return "La carta ${action.instanceId} no está en la mano de $player."
        }
        if (isPlayerFrosted(snapshot, player)) {
            return "$player está congelado y no puede jugar cartas."
        }
        val card = cardOf(snapshot, player, action.instanceId)
            ?: return "No existe la carta."
        if (snapshot.manaOf(player) < card.cost) {
            return "Mana insuficiente: ${snapshot.manaOf(player)} < ${card.cost}."
        }
        return null
    }

    // ---------------------------------------------------------------------
    // Fin de turno
    // ---------------------------------------------------------------------

    private fun endTurn(snapshot: MatchSnapshot): EngineResult {
        // 1) Ticks de estados sobre el avatar del jugador actual.
        val ticked = applyHeroStatusTicks(snapshot, snapshot.currentPlayer)

        // 2) Comprobación de victoria (vida de avatar a 0).
        val victory = resolveVictory(ticked)
        if (victory != null) {
            return EngineResult(victory.finalSnapshot, victory)
        }

        // 3) Avance de turno al siguiente jugador.
        val next = ticked.nextPlayer(ticked.currentPlayer)
        val working = ticked.copy(
            currentPlayer = next,
            turn = ticked.turn + 1,
            log = ticked.log + "Fin de turno. Le toca a $next.",
        )
        return EngineResult(working)
    }

    // ---------------------------------------------------------------------
    // Helpers: estados, ticks y daño sobre el avatar
    // ---------------------------------------------------------------------

    /** Un jugador está congelado si el estado FROST está activo sobre su avatar. */
    private fun isPlayerFrosted(snapshot: MatchSnapshot, player: PlayerId): Boolean =
        snapshot.heroStatusesOf(player).any { it.type == StatusType.FROST }

    private fun opponentOf(snapshot: MatchSnapshot, player: PlayerId): PlayerId =
        snapshot.players.first { it != player }

    /** Reaplica la duración completa a los estados del avatar con decaimiento TIMED. */
    private fun refreshHeroStatuses(snapshot: MatchSnapshot, player: PlayerId): MatchSnapshot {
        val statuses = snapshot.heroStatusesOf(player).map { st ->
            if (st.decay == StatusDecay.TIMED) st.copy(turnsRemaining = st.durationTurns) else st
        }
        return snapshot.copy(heroStatuses = snapshot.heroStatuses + (player to statuses))
    }

    /**
     * Aplica el "tick" de los estados del avatar de [player].
     * BURN/POISON: daño fijo; BLEED: 1d3 con el roller. La vida del avatar se
     * reduce sin bajar de 0.
     */
    private fun applyHeroStatusTicks(snapshot: MatchSnapshot, player: PlayerId): MatchSnapshot {
        var health = snapshot.heroHealthOf(player)
        val updated = snapshot.heroStatusesOf(player).map { st ->
            val damage = when (st.type.damageKind) {
                StatusType.DamageKind.FIXED -> st.type.tickValue
                StatusType.DamageKind.DICE_1D3 -> diceRoller.d(3)
                StatusType.DamageKind.NONE -> 0
            }
            if (damage > 0) {
                // El avatar no tiene defensa por estados en esta versión; el daño
                // se aplica directo (se conserva `ignoresDefense` como propiedad
                // del StatusType para futuros usos).
                health = (health - damage).coerceAtLeast(0)
            }
            // Decrementa la duración de los estados con decaimiento TIMED.
            if (st.decay == StatusDecay.TIMED) st.copy(turnsRemaining = st.turnsRemaining - 1) else st
        }
        // Se conservan los estados a los que aún les quedan ticks.
        val alive = updated.filter { it.decay == StatusDecay.PERMANENT || it.turnsRemaining > 0 }
        return snapshot.copy(
            heroHealth = snapshot.heroHealth + (player to health),
            heroStatuses = snapshot.heroStatuses + (player to alive),
        )
    }

    /** Vincula un bonus pasivo permanente al avatar de [player]. */
    private fun addPassiveBonus(
        snapshot: MatchSnapshot,
        player: PlayerId,
        card: Card,
    ): MatchSnapshot {
        val stat = card.passiveStat ?: return snapshot
        val bonus = PassiveBonus(stat = stat, amount = card.passiveAmount)
        val next = snapshot.passivesOf(player) + bonus
        return snapshot.copy(passives = snapshot.passives + (player to next))
    }

    // ---------------------------------------------------------------------
    // Helpers: resolución de efectos
    // ---------------------------------------------------------------------

    /**
     * Resuelve el [CardEffect] de una carta jugada, siempre sobre avatares:
     * - Attack: daño directo a la vida del avatar del RIVAL.
     * - Heal: cura al avatar objetivo ([targetPlayer] o el actual por defecto)
     *   con tope en su vida máxima.
     * - Draw: robo adicional de cartas respetando el tope de mano 4.
     * - ApplyStatus: impone un estado al avatar objetivo, con neutralización vía
     *   registry.
     */
    private fun resolveEffect(
        snapshot: MatchSnapshot,
        player: PlayerId,
        card: Card,
        targetPlayer: PlayerId?,
    ): MatchSnapshot {
        var working = snapshot
        when (val e = card.effect) {
            is CardEffect.None -> Unit

            is CardEffect.Attack ->
                working = dealDamageToAvatar(working, opponentOf(working, player), e.amount)

            is CardEffect.Heal -> {
                val target = targetPlayer ?: player
                working = healAvatar(working, target, e.amount)
            }

            is CardEffect.Draw -> {
                val currentHand = working.handOf(player)
                val toDraw = (MAX_HAND_SIZE - currentHand.size).coerceAtLeast(0)
                val (drawn, restQueue) = working.drawCards(
                    player = player,
                    target = toDraw.coerceAtMost(e.count),
                    seed = defaultDeckSeed,
                    reshuffleImpl = ::reshuffleDeck,
                )
                working = working.copy(
                    hands = working.hands + (player to currentHand + drawn),
                    decks = working.decks + (player to restQueue),
                )
            }

            is CardEffect.ApplyStatus -> {
                val target = targetPlayer ?: opponentOf(working, player)
                working = applyStatusToAvatar(working, target, e.status, e.durationTurns)
            }

            is CardEffect.PassiveBuff -> Unit
        }
        return working
    }

    /** Inflige [amount] de daño directo a la vida del avatar de [player]. */
    private fun dealDamageToAvatar(
        snapshot: MatchSnapshot,
        player: PlayerId,
        amount: Int,
    ): MatchSnapshot {
        val newHealth = (snapshot.heroHealthOf(player) - amount).coerceAtLeast(0)
        return snapshot.copy(
            heroHealth = snapshot.heroHealth + (player to newHealth),
            log = snapshot.log + "$player recibe $amount de daño en su avatar.",
        )
    }

    /** Cura [amount] al avatar de [player] respetando su vida máxima. */
    private fun healAvatar(
        snapshot: MatchSnapshot,
        player: PlayerId,
        amount: Int,
    ): MatchSnapshot {
        val max = snapshot.heroMaxHealthOf(player)
        val newHealth = (snapshot.heroHealthOf(player) + amount).coerceAtMost(max)
        return snapshot.copy(
            heroHealth = snapshot.heroHealth + (player to newHealth),
            log = snapshot.log + "$player se cura $amount en su avatar.",
        )
    }

    /**
     * Impone un estado al avatar de [player]. Antes de aplicarlo, se consulta el
     * [StatusInteractionRegistry] para neutralizar estados incompatibles (FROST
     * neutraliza BURN). También se refresca la duración si el estado ya existía.
     */
    private fun applyStatusToAvatar(
        snapshot: MatchSnapshot,
        player: PlayerId,
        status: StatusType,
        duration: Int,
    ): MatchSnapshot {
        val current = snapshot.heroStatusesOf(player)

        // Neutralización: eliminar los estados que el nuevo estado neutraliza.
        val afterNeutralize = current.filterNot { existing ->
            StatusInteractionRegistry.neutralizes(status, existing.type)
        }

        // Refresco: si ya existía un estado del mismo tipo, se refresca su
        // duración; si no, se crea.
        val existing = afterNeutralize.firstOrNull { it.type == status }
        val statuses = if (existing != null) {
            afterNeutralize.map { if (it.type == status) it.copy(turnsRemaining = duration) else it }
        } else {
            afterNeutralize + ActiveStatus(
                instanceId = StatusInstanceId.of(player, status, afterNeutralize.size),
                type = status,
                playerId = player,
                durationTurns = duration,
                turnsRemaining = duration,
            )
        }
        return snapshot.copy(
            heroStatuses = snapshot.heroStatuses + (player to statuses),
            log = snapshot.log + "$player recibe el estado ${status.name}.",
        )
    }

    // ---------------------------------------------------------------------
    // Mazo circular y re-barajado
    // ---------------------------------------------------------------------

    /**
     * Re-baraja la pila completa de instancias (determinista si la semilla es
     * fija). El resultado depende de la semilla, de modo que un [SeededDiceRoller]
     * no influye aquí directamente pero la semilla por defecto es estable.
     */
    private fun reshuffleDeck(
        instances: List<InstanceId>,
        seed: Long,
    ): List<InstanceId> {
        val random = kotlin.random.Random(seed)
        return instances.shuffled(random)
    }

    // ---------------------------------------------------------------------
    // Victoria
    // ---------------------------------------------------------------------

    /**
     * Devuelve un [MatchResult] si hay un ganador, si no null.
     *
     * Condición de victoria del rediseño: cuando la vida del avatar de un jugador
     * llega a 0, gana el otro ([WinReason.OPPONENT_DEFEATED]) y la partida pasa a
     * [MatchSnapshot.Phase.FINISHED].
     *
     * Precedencia en doble KO: primero se declara derrotado al RIVAL del jugador
     * actual (su avatar llegó a 0 durante el turno en curso); solo si el rival
     * sigue vivo se declara derrotado el jugador actual (p. ej. por ticks de
     * estados al terminar su turno).
     */
    private fun resolveVictory(snapshot: MatchSnapshot): MatchResult? {
        if (snapshot.phase == MatchSnapshot.Phase.FINISHED) return null
        val current = snapshot.currentPlayer
        val defeated = snapshot.players.firstOrNull {
            it != current && snapshot.heroHealthOf(it) <= 0
        } ?: snapshot.players.firstOrNull { snapshot.heroHealthOf(it) <= 0 } ?: return null
        val winner = opponentOf(snapshot, defeated)
        val finalSnap = snapshot.copy(
            phase = MatchSnapshot.Phase.FINISHED,
            winner = winner,
            log = snapshot.log + "$winner gana: el avatar de $defeated se quedó sin vida.",
        )
        return MatchResult(
            matchId = snapshot.matchId,
            winner = winner,
            reason = WinReason.OPPONENT_DEFEATED,
            finalSnapshot = finalSnap,
        )
    }

    /** Busca el CardId correspondiente a una instancia en el snapshot. */
    private fun cardOf(
        snapshot: MatchSnapshot,
        player: PlayerId,
        instanceId: InstanceId,
    ): Card? {
        // La resolución instancia -> definición de carta viene en el snapshot
        // (poblada por la MatchFactory al crear mazos).
        val cardId = snapshot.cardOf[instanceId] ?: return null
        return catalog.findById(cardId)
    }
}
