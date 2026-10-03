package com.cardclash.data.local.mapper

import com.cardclash.data.local.entity.DeckCardEntity
import com.cardclash.data.local.entity.DeckEntity
import com.cardclash.data.local.entity.PlayerProfileEntity
import com.cardclash.data.local.model.Deck
import com.cardclash.data.local.model.DeckCard
import com.cardclash.data.local.model.DeckId
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.PlayerId

/**
 * Mapeos puros entidad Room <-> dominio local para mazos.
 *
 * Kotlin PURO (sin tocar el runtime de Room): las entidades son data classes
 * con anotaciones inertes, por lo que estos mapeos se testean con unit tests
 * JVM (el testing-specialist los anadira).
 *
 * Conversiones clave:
 * - DeckEntity + List<DeckCardEntity> <-> Deck (con DeckId, DeckCard)
 * - DeckCardEntity <-> DeckCard (slot + CardId)
 */
object DeckMapper {

    // ------------------------------------------------------------------
    // Entity -> Domain
    // ------------------------------------------------------------------

    /**
     * Convierte entidad + lista de cartas a modelo de dominio [Deck].
     * Las cartas deben venir ya ordenadas por slot (0..8).
     */
    fun toDomain(
        entity: DeckEntity,
        cards: List<DeckCardEntity>,
    ): Deck {
        val domainCards = cards.sortedBy { it.slot }.map { toDeckCard(it) }
        return Deck(
            id = DeckId.fromString(entity.id),
            name = entity.name,
            isActive = entity.isActive,
            playerId = PlayerId(entity.playerId),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            cards = domainCards,
        )
    }

    /** Convierte una sola entidad DeckCardEntity a DeckCard de dominio. */
    fun toDeckCard(entity: DeckCardEntity): DeckCard =
        DeckCard(slot = entity.slot, cardId = CardId(entity.cardId))

    /**
     * Convierte [DeckWithCards] (POJO @Relation) a [Deck] de dominio.
     * Conveniente para usar directamente el resultado de DeckDao.getDeckWithCards.
     */
    fun toDomain(withCards: com.cardclash.data.local.model.DeckWithCards): Deck =
        toDomain(withCards.deck, withCards.cards)

    // ------------------------------------------------------------------
    // Domain -> Entity
    // ------------------------------------------------------------------

    /**
     * Convierte [Deck] de dominio a par (DeckEntity, List<DeckCardEntity>)
     * listo para persistir con DeckDao.
     * Genera nuevo DeckId si el deck no tiene uno (id vacio).
     */
    fun toEntity(deck: Deck): Pair<DeckEntity, List<DeckCardEntity>> {
        val entity = DeckEntity(
            id = deck.id.value,
            name = deck.name,
            isActive = deck.isActive,
            playerId = deck.playerId.value,
            createdAt = deck.createdAt,
            updatedAt = deck.updatedAt,
        )
        val cardEntities = deck.cards.sortedBy { it.slot }.mapIndexed { index, card ->
            // Usamos el slot del dominio; si hay huecos, los respetamos.
            // El dominio garantiza slots unicos en 0..8.
            DeckCardEntity(
                deckId = deck.id.value,
                slot = card.slot,
                cardId = card.cardId.value,
            )
        }
        return entity to cardEntities
    }

    /**
     * Crea un DeckEntity nuevo para insercion (genera UUID y timestamps).
     * Util para el repositorio al crear un mazo desde cero.
     */
    fun toNewEntity(
        name: String,
        playerId: PlayerId = PlayerId(PlayerProfileEntity.DEFAULT_PLAYER_ID),
        clock: () -> Long = System::currentTimeMillis,
    ): DeckEntity {
        val now = clock()
        val id = java.util.UUID.randomUUID().toString()
        return DeckEntity(
            id = id,
            name = name,
            isActive = false,
            playerId = playerId.value,
            createdAt = now,
            updatedAt = now,
        )
    }

    // ------------------------------------------------------------------
    // DeckCardEntity factory
    // ------------------------------------------------------------------

    /** Crea DeckCardEntity para insercion batch. */
    fun toCardEntity(deckId: String, slot: Int, cardId: CardId): DeckCardEntity =
        DeckCardEntity(deckId = deckId, slot = slot, cardId = cardId.value)
}