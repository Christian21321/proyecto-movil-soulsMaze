package com.cardclash.ui.navigation

/**
 * Rutas del grafo de navegacion local de CardClash.
 */
object Routes {

    /** Menu principal con el progreso resumido. */
    const val HOME = "home"

    /** Coleccion de cartas + progresion de nivel/tramo/XP. */
    const val COLLECTION = "collection"

    /** Historial de partidas registradas. */
    const val HISTORY = "history"

    /** Pantalla de combate (modo local demo por defecto). */
    const val BATTLE = "battle"

    /** Constructor de mazos (DeckBuilder). deckId opcional para editar existente. */
    const val DECK_BUILDER = "deck_builder"

    /** Argumento opcional: ID del mazo a editar (UUID string). */
    const val ARG_DECK_ID = "deckId"
}