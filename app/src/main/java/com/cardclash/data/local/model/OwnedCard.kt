package com.cardclash.data.local.model

import com.cardclash.domain.model.CardId

/**
 * Tenencia de una carta en la coleccion local (valor inmutable, Kotlin puro).
 *
 * Es el mapeo de dominio de [com.cardclash.data.local.entity.OwnedCardEntity].
 */
data class OwnedCard(
    val cardId: CardId,
    val ownedCount: Int,
)