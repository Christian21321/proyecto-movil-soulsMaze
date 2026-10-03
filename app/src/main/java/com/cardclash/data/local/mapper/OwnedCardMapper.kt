package com.cardclash.data.local.mapper

import com.cardclash.data.local.entity.OwnedCardEntity
import com.cardclash.data.local.model.OwnedCard
import com.cardclash.domain.model.CardId
import com.cardclash.domain.progression.CardCollection

/**
 * Mapeos puros entidad Room <-> dominio para la coleccion de cartas.
 *
 * Kotlin PURO (sin tocar el runtime de Room): las entidades son data classes
 * con anotaciones inertes, por lo que estos mapeos se testean con unit tests
 * JVM ([com.cardclash.data.local.OwnedCardMapperTest]).
 */
object OwnedCardMapper {

    /** Convierte [CardId] + numero de copias a fila Room. */
    fun toEntity(cardId: CardId, copies: Int): OwnedCardEntity =
        OwnedCardEntity(cardId.value, copies)

    /** Convierte una fila Room al modelo de dominio de tenencia. */
    fun toOwnedCard(entity: OwnedCardEntity): OwnedCard =
        OwnedCard(CardId(entity.cardId), entity.ownedCount)

    /**
     * Reconstruye la [CardCollection] inmutable del dominio desde las filas Room.
     * El constructor de CardCollection es publico (solo la propiedad es privada),
     * por lo que se puede reconstruir sin tocar el dominio.
     */
    fun toCollection(entities: List<OwnedCardEntity>): CardCollection =
        CardCollection(entities.associate { CardId(it.cardId) to it.ownedCount })

    /** Proyecta la coleccion del dominio a filas Room (una por carta poseida). */
    fun fromCollection(collection: CardCollection): List<OwnedCardEntity> =
        collection.ownedCards().map { OwnedCardEntity(it.value, collection.ownedCount(it)) }
}