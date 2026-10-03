package com.cardclash.domain.battle

import com.cardclash.data.remote.dto.ClientProjectionWire
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PassiveBonus
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.StatusType
import com.cardclash.domain.model.UnitStat
import com.cardclash.domain.repository.CardCatalog

/**
 * Mapeo PURO de una fuente de dominio (snapshot del host o proyección del
 * cliente) a un [CombatUiState] listo para Compose.
 *
 * Resuelve nombres/rareza vía [CardCatalog], marca [HandCardUi.playable] y
 * expone la vida de ambos avatares (sin tableros, tras la Fase 1). Aplica el
 * límite de mensajes de log. Es una función pura (misma entrada ⇒ misma salida)
 * testeable en JVM.
 */
object CombatReducer {

    private const val BASE_MAX_MANA = 10

    /**
     * Construye la vista desde el snapshot completo (modo host / local).
     *
     * @param snapshot estado del motor a representar.
     * @param catalog  catálogo para resolver nombres/rareza.
     * @param myId     identidad del jugador local ("yo").
     * @param maxLogLines número máximo de mensajes de log a exponer.
     */
    fun fromSnapshot(
        snapshot: MatchSnapshot,
        catalog: CardCatalog,
        myId: PlayerId,
        maxLogLines: Int = 8,
    ): CombatUiState {
        val opponent = snapshot.players.first { it != myId }
        val myFrosted = isFrosted(snapshot.heroStatusesOf(myId).map { it.type })

        val myHand = snapshot.handOf(myId).map { instance ->
            val cardId = snapshot.cardOf[instance]
            val card = cardId?.let(catalog::findById)
            val playable = myId == snapshot.currentPlayer &&
                snapshot.phase == MatchSnapshot.Phase.PLAYING &&
                card != null &&
                snapshot.manaOf(myId) >= card.cost &&
                !myFrosted
            HandCardUi(
                instanceId = instance,
                cardId = cardId ?: CardId(instance.value),
                name = card?.name ?: cardId?.value ?: instance.value,
                cost = card?.cost ?: 0,
                attack = card?.attack ?: 0,
                maxHealth = card?.maxHealth ?: 0,
                rarity = cardId?.let(catalog::rarityOf),
                isPassive = card?.isPassive ?: false,
                playable = playable,
            )
        }

        return CombatUiState(
            matchId = snapshot.matchId,
            phase = snapshot.phase,
            turn = snapshot.turn,
            currentPlayer = snapshot.currentPlayer,
            myId = myId,
            isMyTurn = snapshot.isCurrent(myId),
            myMana = snapshot.manaOf(myId),
            maxMana = snapshot.effectiveMaxMana(myId),
            myHand = myHand,
            myHeroHealth = snapshot.heroHealthOf(myId),
            myHeroMaxHealth = snapshot.heroMaxHealthOf(myId),
            opponentHeroHealth = snapshot.heroHealthOf(opponent),
            opponentHeroMaxHealth = snapshot.heroMaxHealthOf(opponent),
            opponentHandSize = snapshot.handOf(opponent).size,
            opponentMana = snapshot.manaOf(opponent),
            log = snapshot.log.takeLast(maxLogLines),
            winner = snapshot.winner,
            myDeckSize = snapshot.deckOf(myId).size,
            opponentDeckSize = snapshot.deckOf(opponent).size,
        )
    }

    /**
     * Construye la vista desde una proyección de cliente ([ClientProjectionWire]).
     *
     * El cliente NO conoce la mano del rival (solo el conteo) ni los mazos, por lo
     * que [opponentHandSize] proviene de la proyección y [myHand] se resuelve con
     * el mapa propio [myCardOf] (instancias -> definición).
     *
     * TODO (Fase 2, capas de sync/DTO): [ClientProjectionWire] debe reemplazar su
     * campo `boards: Map<PlayerId, List<UnitState>>` (ya eliminado del dominio)
     * por [heroHealth]/[heroMaxHealth] por jugador. Esta firma ya se escribe contra
     * ese contrato futuro; no compilará hasta que el DTO se actualice en Fase 2.
     *
     * @param projection proyección recibida del host.
     * @param catalog catálogo para resolver nombres/rareza.
     * @param myCardOf mapa privado del cliente instancia -> cardId (su propia mano).
     * @param maxLogLines número máximo de mensajes de log a exponer.
     */
    fun fromClientProjection(
        projection: ClientProjectionWire,
        catalog: CardCatalog,
        myCardOf: Map<InstanceId, CardId>,
        maxLogLines: Int = 8,
    ): CombatUiState {
        val myId = projection.clientPlayer
        val opponent = projection.players.first { it != myId }
        val myFrosted = isFrosted(projection.heroStatuses[myId].orEmpty().map { it.type })
        val myMana = projection.manas[myId] ?: 0

        val myHand = projection.clientHand.map { instance ->
            val cardId = myCardOf[instance]
            val card = cardId?.let(catalog::findById)
            val playable = myId == projection.currentPlayer &&
                projection.phase == MatchSnapshot.Phase.PLAYING &&
                card != null &&
                myMana >= card.cost &&
                !myFrosted
            HandCardUi(
                instanceId = instance,
                cardId = cardId ?: CardId(instance.value),
                name = card?.name ?: cardId?.value ?: instance.value,
                cost = card?.cost ?: 0,
                attack = card?.attack ?: 0,
                maxHealth = card?.maxHealth ?: 0,
                rarity = cardId?.let(catalog::rarityOf),
                isPassive = card?.isPassive ?: false,
                playable = playable,
            )
        }

        return CombatUiState(
            matchId = projection.matchId,
            phase = projection.phase,
            turn = projection.turn,
            currentPlayer = projection.currentPlayer,
            myId = myId,
            isMyTurn = projection.currentPlayer == myId,
            myMana = myMana,
            maxMana = MatchSnapshot.manaCapFor(projection.players, myId, projection.turn, BASE_MAX_MANA) +
                passiveManaBonus(projection.passives[myId].orEmpty()),
            myHand = myHand,
            myHeroHealth = projection.heroHealth[myId] ?: 0,
            myHeroMaxHealth = projection.heroMaxHealth[myId] ?: 0,
            opponentHeroHealth = projection.heroHealth[opponent] ?: 0,
            opponentHeroMaxHealth = projection.heroMaxHealth[opponent] ?: 0,
            opponentHandSize = projection.numCardsInHostHand,
            opponentMana = projection.manas[opponent] ?: 0,
            log = projection.log.takeLast(maxLogLines),
            winner = projection.winner,
            // La proyección no expone los mazos (secreto): tamaño desconocido.
            myDeckSize = null,
            opponentDeckSize = null,
        )
    }

    /** true si la lista de estados del avatar contiene FROST. */
    private fun isFrosted(statuses: List<StatusType>): Boolean =
        statuses.any { it == StatusType.FROST }

    /** Suma de las pasivas MAX_MANA del avatar (se añade al tope creciente). */
    private fun passiveManaBonus(passives: List<PassiveBonus>): Int =
        passives.sumOf { bonus ->
            if (bonus.stat == UnitStat.MAX_MANA) bonus.amount else 0
        }
}
