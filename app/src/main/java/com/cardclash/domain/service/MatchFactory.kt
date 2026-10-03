package com.cardclash.domain.service

import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.engine.CombatEngine
import com.cardclash.domain.repository.CardCatalog


/**
 * Fábrica de partidas (y de sus mazos) de CardClash.
 *
 * Su responsabilidad es la "poblar" los datos estáticos que el motor necesita
 * para funcionar correctamente:
 * - Construir un mazo por jugador a partir de una descripción de cartas
 *   ([CardId] repetibles), asignando a cada copia un [InstanceId] único.
 * - Mantener el mapa de resolución `InstanceId -> CardId` en el snapshot para
 *   que el motor pueda saber qué carta es cada instancia.
 * - Delegar la creación del snapshot inicial al motor ([CombatEngine.newMatch]).
 *
 * Todo es Kotlin/JVM PURO; no depende de android.*.
 */
class MatchFactory(
    private val catalog: CardCatalog,
    private val engine: CombatEngine,
) {

    /**
     * Construye las instancias de un mazo a partir de una lista de [CardId].
     * Cada aparición recibe un [InstanceId] propio, enlazado en el mapa
     * resultante instancia -> cardId.
     *
     * El [InstanceId] queda ESCOPEADO por [player] (se antepone su [PlayerId])
     * para garantizar que, cuando dos jugadores usan las mismas cartas, sus
     * instancias NUNCA colisionen entre sí.
     */
    fun buildDeck(
        player: PlayerId,
        deckSpec: List<CardId>,
    ): Pair<List<InstanceId>, Map<InstanceId, CardId>> {
        val instances = mutableListOf<InstanceId>()
        val mapping = mutableMapOf<InstanceId, CardId>()
        deckSpec.forEach { cardId ->
            val instance = instances.size
            val instId = InstanceId("${player.value}/$instance/instance/${cardId.value}")
            instances += instId
            mapping[instId] = cardId
        }
        return instances to mapping
    }

    /**
     * Crea una nueva partida poblada.
     *
     * @param matchId identificador de la partida.
     * @param players jugadores en orden de turno.
     * @param deckByPlayer especificación del mazo (lista de CardId) por jugador.
     * @param heroLevelByPlayer nivel del avatar de cada jugador (opcional; por
     *   defecto nivel 1 -> 30 de vida máxima). Fija la vida máxima del avatar.
     * @param initialHandSize tamaño inicial de la mano (por defecto 3).
     * @param shuffleDecks baraja los mazos antes de repartir (por defecto true).
     */
    fun newMatch(
        matchId: MatchId,
        players: List<PlayerId>,
        deckByPlayer: Map<PlayerId, List<CardId>>,
        heroLevelByPlayer: Map<PlayerId, Int> = emptyMap(),
        initialHandSize: Int = CombatEngine.DEFAULT_INITIAL_HAND_SIZE,
        shuffleDecks: Boolean = true,
    ): MatchSnapshot {
        // Construye instancias y resolución por jugador, escopeando cada
        // InstanceId por jugador para que no colisionen entre mazos.
        val deckInstances = players.associateWith { player ->
            buildDeck(player, deckByPlayer[player].orEmpty())
        }
        val instantiatedDecks = deckInstances.mapValues { (_, pair) -> pair.first }
        val cardOf = deckInstances.flatMap { (_, pair) -> pair.second.entries }.associate { it.key to it.value }

        val snapshot = engine.newMatch(
            matchId = matchId,
            players = players,
            deckByPlayer = instantiatedDecks,
            heroLevelByPlayer = heroLevelByPlayer,
            initialHandSize = initialHandSize,
            shuffleDecks = shuffleDecks,
        )
        // Poblado de la resolución instancia -> carta.
        return snapshot.copy(cardOf = cardOf)
    }
}
