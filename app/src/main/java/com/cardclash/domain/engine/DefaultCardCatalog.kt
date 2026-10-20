package com.cardclash.domain.engine

import com.cardclash.domain.model.Card
import com.cardclash.domain.model.CardEffect
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.DiceExpr
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
 * - ataques directos al avatar ([CardEffect.Attack]) con dados D&D,
 * - curación del avatar ([CardEffect.Heal]) con dados D&D,
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
        // Los estados de daño duran 2 turnos (BURN 1d4x2 ~5, POISON 1d6x2 ~7,
        // BLEED 1d3 + 1d3+1 ~5) y cuestan como un ataque directo de daño
        // parecido. FROST dura 1 turno (salvación d20 CD 12 del rival): el rival
        // salta un turno y juega el siguiente, de modo que no se puede encadenar
        // un bloqueo permanente.
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
     * avatar rival tirando sus dados. [attack]/[maxHealth] se conservan por
     * compatibilidad pero ya no representan una unidad en tablero ([attack] es
     * el daño máximo de la tirada normal).
     *
     * Dado según el coste, con daño medio = 2 x coste + 1.5 a 3 (coste 1 ->
     * 1d4+1 / 1d6+1, coste 2 -> 1d8+1 / 1d10+1, coste 3 -> 1d12+1 / 2d6+1).
     */
    private fun baseAttack(i: Int): Card {
        val dice = ATTACK_DICE[i]
        return Card(
            id = CardId("attack-$i"),
            name = "Golpe $i",
            cost = 1 + i / 2,
            attack = dice.max,
            maxHealth = 4 + i,
            effect = CardEffect.Attack(dice),
        )
    }

    /** Carta de curación base genérica: cura el avatar objetivo tirando sus dados. */
    private fun baseHeal(i: Int): Card = Card(
        id = CardId("heal-$i"),
        name = "Poción $i",
        cost = 1 + (i + 1) / 2,
        attack = 1,
        maxHealth = 3,
        effect = CardEffect.Heal(HEAL_DICE[i]),
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
        /** Dados de cada carta de ataque (`attack-0` .. `attack-5`). */
        private val ATTACK_DICE: List<DiceExpr> = listOf(
            DiceExpr(1, 4, 1),
            DiceExpr(1, 6, 1),
            DiceExpr(1, 8, 1),
            DiceExpr(1, 10, 1),
            DiceExpr(1, 12, 1),
            DiceExpr(2, 6, 1),
        )

        /** Dados de cada carta de curación (`heal-0` .. `heal-2`). */
        private val HEAL_DICE: List<DiceExpr> = listOf(
            DiceExpr(1, 6, 1),
            DiceExpr(2, 4),
            DiceExpr(1, 8, 2),
        )

        /** Duración de los estados de daño (BURN, POISON, BLEED). */
        const val STATUS_DURATION_TURNS: Int = 2

        /** Duración de FROST: 1 turno para que no pueda bloquear al rival indefinidamente. */
        const val FROST_DURATION_TURNS: Int = 1
    }
}
