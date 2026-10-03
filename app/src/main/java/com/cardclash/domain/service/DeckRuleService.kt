package com.cardclash.domain.service

import com.cardclash.domain.model.CardId
import com.cardclash.domain.repository.CardCatalog

/**
 * Reglas de construcción y validación de mazos de CardClash.
 *
 * Separa la lógica de "qué mazo es legal" del motor de combate y de la fábrica
 * de partidas: así, los tests pueden verificar reglas de negocio de forma
 * aislada y el motor puede asumir mazos ya validados.
 *
 * ## Modelo consolidado (Etapa 1) alineado
 *
 * - Tamaño máximo de deck (cartas NO pasivas): [maxDeckSize] (por defecto 8).
 * - Sin duplicados: maximo [maxCopiesPerCard] (por defecto 1) de una misma
 *   carta en el mazo.
 * - A lo sumo [maxPassiveCards] (por defecto 1) carta PASIVA MAX_MANA como
 *   EXTRA (no cuenta para el limite de 8); el resto del mazo son cartas de
 *   ataque/efecto.
 * - Todo [CardId] debe existir en el catálogo.
 */
class DeckRuleService(
    private val catalog: CardCatalog,
    val maxDeckSize: Int = 8,
    val maxCopiesPerCard: Int = 1,
    val maxPassiveCards: Int = 1,
) {

    /** Resultado de la validación de un mazo. */
    data class DeckValidation(
        val valid: Boolean,
        val errors: List<String>,
    ) {
        /** true si el mazo cumple todas las reglas. */
        val isOk: Boolean get() = valid && errors.isEmpty()
    }

    /**
     * Valida una lista de [CardId] como mazo legal según las reglas configuradas.
     * Retorna un [DeckValidation] con los errores encontrados (si los hay).
     */
    fun validate(deckSpec: List<CardId>): DeckValidation {
        val errors = mutableListOf<String>()

        // Regla 0: cada carta debe existir en el catálogo.
        val unknown = deckSpec.filter { catalog.findById(it) == null }
        if (unknown.isNotEmpty()) {
            errors += "Cartas desconocidas en el catálogo: $unknown."
        }

        // Regla 1: sin duplicados (max 1 copia por carta en el deck).
        val duplicated = deckSpec.groupingBy { it }.eachCount().filter { it.value > maxCopiesPerCard }
        duplicated.forEach { (cardId, count) ->
            errors += "La carta $cardId aparece $count veces (máx $maxCopiesPerCard)."
        }

        // Regla 2: a lo sumo 1 pasiva MAX_MANA (y como extra).
        val passives = deckSpec.filter { catalog.findById(it)?.isPassive == true }
        if (passives.size > maxPassiveCards) {
            errors += "El mazo tiene ${passives.size} cartas pasivas (máx $maxPassiveCards)."
        }

        // Regla 3: tamaño maximo de cartas NO pasivas <= maxDeckSize.
        val nonPassive = deckSpec.filter { catalog.findById(it)?.isPassive != true }
        if (nonPassive.size > maxDeckSize) {
            errors += "El mazo tiene ${nonPassive.size} cartas no pasivas (máx $maxDeckSize)."
        }

        return DeckValidation(valid = errors.isEmpty(), errors = errors)
    }
}
