package com.cardclash.data.remote.firebase

/**
 * Construccion de las rutas de Firestore del esquema bifurcado de ADR-009.
 */
object FirestorePaths {

    /** Coleccion raiz de partidas. */
    const val MATCHES = "matches"

    /** Coleccion de acciones por partida. */
    const val ACTIONS = "actions"

    /** Nombre fijo de los documentos de estado (bifurcado). */
    const val CURRENT = "current"

    fun match(matchId: String) = "$MATCHES/$matchId"

    fun stateHost(matchId: String) = "$MATCHES/$matchId/stateHost/$CURRENT"

    fun stateClient(matchId: String) = "$MATCHES/$matchId/stateClient/$CURRENT"

    fun actions(matchId: String) = "$MATCHES/$matchId/$ACTIONS"
}
