package com.cardclash.data.remote.dto

/**
 * Constantes de los nombres de campo usados en el wire (mapeo Firestore) de los
 * documentos `stateHost` y `stateClient` (ADR-009).
 *
 * Centralizar los nombres aqui evita typos y mantiene sincronizado el
 * serializador/deserializador con las reglas Firestore y la UI.
 */
object WireFields {

    // ---- Compartidos entre stateHost y stateClient ----
    const val MATCH_ID = "matchId"
    const val PHASE = "phase"
    const val TURN = "turn"
    const val CURRENT_PLAYER = "currentPlayer"
    const val PLAYERS = "players"
    const val MANAS = "manas"
    const val PLAYED_CARD_LAST_TURN = "playedCardLastTurn"
    const val WINNER = "winner"
    const val LOG = "log"
    const val VERSION = "version"

    // ---- Estado del avatar (reemplaza a los tableros de unidades, Fase 2a) ----
    const val HERO_HEALTH = "heroHealth"
    const val HERO_MAX_HEALTH = "heroMaxHealth"
    const val HERO_STATUSES = "heroStatuses"
    const val PASSIVES = "passives"

    // ---- Solo stateHost (snapshot completo, host) ----
    const val PROCESSED_ACTION_SEQ = "processedActionSeq"
    const val HANDS = "hands"
    const val DECKS = "decks"
    const val FULL_DECKS = "fullDecks"
    const val CARD_OF = "cardOf"
    const val BASE_MAX_MANA = "baseMaxMana"

    // ---- Solo stateClient (proyeccion filtrada, cliente) ----
    const val CLIENT_PLAYER = "clientPlayer"
    const val CLIENT_HAND = "clientHand"
    const val NUM_CARDS_IN_HOST_HAND = "numCardsInHostHand"

    // ---- Campos de la pasiva (PassiveBonus: stat + amount) ----
    const val PASSIVE_STAT = "stat"
    const val PASSIVE_AMOUNT = "amount"

    // ---- Campos de ActiveStatus (estado sobre el avatar) ----
    const val STATUS_INSTANCE_ID = "statusInstanceId"
    const val STATUS_TYPE = "statusType"
    const val PLAYER_ID = "playerId"
    const val DURATION_TURNS = "durationTurns"
    const val TURNS_REMAINING = "turnsRemaining"
    const val DECAY = "decay"
}
