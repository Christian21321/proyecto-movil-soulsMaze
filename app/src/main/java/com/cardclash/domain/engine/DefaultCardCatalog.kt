package com.cardclash.domain.engine

import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardEffect
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.Rarity
import com.cardclash.domain.model.StatusType
import com.cardclash.domain.model.UnitStat
import com.cardclash.domain.repository.CardCatalog

/**
 * Catálogo de cartas en memoria (mapa inmutable) para el motor de combate.
 *
 * Sirve de implementación de referencia de [CardCatalog] y proporciona cartas
 * de ejemplo que cubren todos los [CardEffect] del dominio. Tras el rediseño de
 * Fase 1 (sin unidades), los efectos apuntan siempre al AVATAR:
 * - ataques directos al avatar ([CardEffect.Attack]),
 * - curación del avatar ([CardEffect.Heal]),
 * - robo ([CardEffect.Draw]),
 * - imposición de estados al avatar (BURN, POISON, BLEED, FROST),
 * - pasivas de mana vinculadas al avatar ([CardEffect.PassiveBuff] sobre MAX_MANA).
 *
 * En etapas posteriores, un catálogo real respaldado por Base de Datos
 * reemplazará (o complementará) a esta implementación sin cambiar el motor.
 */
class DefaultCardCatalog : CardCatalog {

    private val cardsById: Map<CardId, Card> = buildList {
        // --- Cartas de ataque ---
        addAll(List(6) { i -> baseAttack(i) })

        // --- Cartas de curación ---
        addAll(List(3) { i -> baseHeal(i) })

        // --- Cartas de robo ---
        addAll(List(3) { i -> baseDraw(i) })

        // --- Cartas de estados (una por tipo) ---
        // Los estados de daño duran 2 turnos (BURN 2x2=4, POISON 3x2=6, BLEED
        // 1d3x2 ~4) y cuestan como un ataque directo de daño parecido. FROST
        // dura 1 turno: el rival salta un turno y juega el siguiente, de modo
        // que no se puede encadenar un bloqueo permanente.
        add(statusCard(StatusType.BURN, cost = 2))
        add(statusCard(StatusType.POISON, cost = 3))
        add(statusCard(StatusType.BLEED, cost = 2))
        add(statusCard(StatusType.FROST, cost = 5, durationTurns = FROST_DURATION_TURNS))

        // --- Cartas pasivas de mana ---
        add(passiveCard(UnitStat.MAX_MANA, amount = +2, cost = 4))
        add(passiveCard(UnitStat.MAX_MANA, amount = +1, cost = 3))
    }.associateBy { it.id }

    /**
     * Rareza asignada a cada carta del catalogo. La rareza es dato de
     * progresion/coleccion (no de combate) y se asigna de forma acorde al
     * coste de mana declarado: las cartas mas caras (mayor rareza) son mas
     * escasas y mas dificiles de coleccionar. Esto mantiene la coherencia con
     * la convencion [Rarity.manaCost] (COMMON=3, SR=4, SSR=5).
     */
    private val rarityById: Map<CardId, Rarity> = cardsById.mapValues { (_, card) ->
        when (card.cost) {
            in 5..Int.MAX_VALUE -> Rarity.SSR
            4 -> Rarity.SR
            else -> Rarity.COMMON
        }
    }

    /**
     * Carta de ataque base genérica: inflige [CardEffect.Attack] directo al
     * avatar rival. [attack]/[maxHealth] se conservan por compatibilidad pero ya
     * no representan una unidad en tablero.
     *
     * Coste proporcional al daño: daño = 2 x coste + 1 o + 2 (coste 1 -> 3-4,
     * coste 2 -> 5-6, coste 3 -> 7-8).
     */
    private fun baseAttack(i: Int): Card = Card(
        id = CardId("attack-$i"),
        name = "Golpe $i",
        cost = 1 + i / 2,
        attack = 3 + i,
        maxHealth = 4 + i,
        effect = CardEffect.Attack(amount = 3 + i),
    )

    /** Carta de curación base genérica: cura el avatar objetivo (4 por 1, 5-6 por 2). */
    private fun baseHeal(i: Int): Card = Card(
        id = CardId("heal-$i"),
        name = "Poción $i",
        cost = 1 + (i + 1) / 2,
        attack = 1,
        maxHealth = 3,
        effect = CardEffect.Heal(amount = 4 + i),
    )

    /** Carta de robo base genérica: 1 mana por carta robada. */
    private fun baseDraw(i: Int): Card = Card(
        id = CardId("draw-$i"),
        name = "Reflexión $i",
        cost = 1 + i,
        attack = 0,
        maxHealth = 2,
        effect = CardEffect.Draw(count = 1 + i),
    )

    /** Carta que impone un estado al avatar objetivo con duración. */
    private fun statusCard(
        type: StatusType,
        cost: Int,
        durationTurns: Int = STATUS_DURATION_TURNS,
    ): Card = Card(
        id = CardId("status-${type.name.lowercase()}"),
        name = "Aplicar ${type.name}",
        cost = cost,
        attack = 1,
        maxHealth = 3,
        effect = CardEffect.ApplyStatus(status = type, durationTurns = durationTurns),
    )

    /** Carta PASIVA: no dispara efecto, solo aporta un modificador al avatar. */
    private fun passiveCard(stat: UnitStat, amount: Int, cost: Int): Card = Card(
        id = CardId("passive-${stat.name.lowercase()}-${if (amount > 0) "+$amount" else amount}"),
        name = "Aura de ${stat.name} $amount",
        cost = cost,
        attack = 0,
        maxHealth = 2,
        effect = CardEffect.None,
        passiveStat = stat,
        passiveAmount = amount,
        isPassive = true,
    )

    override fun findById(id: CardId): Card? = cardsById[id]

    override fun all(): List<Card> = cardsById.values.toList()

    override fun rarityOf(id: CardId): Rarity? = rarityById[id]

    companion object {
        /** Duración de los estados de daño (BURN, POISON, BLEED). */
        const val STATUS_DURATION_TURNS: Int = 2

        /** Duración de FROST: 1 turno para que no pueda bloquear al rival indefinidamente. */
        const val FROST_DURATION_TURNS: Int = 1
    }
}
