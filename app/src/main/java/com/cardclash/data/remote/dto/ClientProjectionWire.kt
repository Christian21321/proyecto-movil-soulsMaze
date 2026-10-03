package com.cardclash.data.remote.dto

import com.cardclash.domain.model.ActiveStatus
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PassiveBonus
import com.cardclash.domain.model.PlayerId

/**
 * Proyeccion filtrada del documento `stateClient` de Firestore (ADR-009).
 *
 * Representa TODO lo que el cliente conoce de la partida sin revelar secretos:
 * nunca incluye la mano del host, [MatchSnapshot.decks], [MatchSnapshot.fullDecks]
 * ni [MatchSnapshot.cardOf]. [clientHand] es la mano del propio cliente y
 * [numCardsInHostHand] solo expone el CONTEO de cartas del host.
 *
 * [clientPlayer] identifica al jugador local para poder correlacionar la
 * proyeccion con su identidad al reconstruirla.
 */
data class ClientProjectionWire(
    val matchId: com.cardclash.domain.model.MatchId,
    val phase: MatchSnapshot.Phase,
    val turn: Int,
    val currentPlayer: PlayerId,
    val players: List<PlayerId>,
    val heroHealth: Map<PlayerId, Int>,
    val heroMaxHealth: Map<PlayerId, Int>,
    val heroStatuses: Map<PlayerId, List<ActiveStatus>>,
    val passives: Map<PlayerId, List<PassiveBonus>>,
    val manas: Map<PlayerId, Int>,
    val playedCardLastTurn: Map<PlayerId, Boolean>,
    val winner: PlayerId?,
    val log: List<String>,
    val clientPlayer: PlayerId,
    val clientHand: List<InstanceId>,
    val numCardsInHostHand: Int,
    val version: Long,
)
