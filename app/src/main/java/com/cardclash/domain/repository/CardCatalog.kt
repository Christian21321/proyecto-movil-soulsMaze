package com.cardclash.domain.repository

import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.Rarity

/**
 * Interfaz de consulta del catalogo de cartas de CardClash.
 *
 * Es una "inversión de dependencia": el motor (en `engine`) trabaja contra esta
 * interfaz, no contra una implementación concreta. Esto permite:
 * - usar [com.cardclash.domain.engine.DefaultCardCatalog] para el catálogo en
 *   memoria,
 * - o una implementación respaldada por Room/BD en capas superiores sin tocar
 *   el motor.
 *
 * El motor de combate es Kotlin/JVM PURO y no depende de android.*; el catálogo
 * se inyecta al motor.
 */
interface CardCatalog {

    /**
     * Devuelve la [Card] definida por [id], o null si el catálogo no la conoce.
     */
    fun findById(id: CardId): Card?

    /**
     * Devuelve todas las cartas registradas en el catálogo.
     */
    fun all(): List<Card>

    /**
     * Devuelve la [Rarity] de la carta [id], o null si el catalogo no la conoce
     * o no declara rareza.
     *
     * La rareza es dato de PROGRESION/COLECCION (no de combate), por lo que la
     * interfaz la expone de forma opcional: las implementaciones que solo
     * alimentan el motor (o los catalogos de test) pueden devolver null sin
     * afectar al combate.
     */
    fun rarityOf(id: CardId): Rarity? = null
}
