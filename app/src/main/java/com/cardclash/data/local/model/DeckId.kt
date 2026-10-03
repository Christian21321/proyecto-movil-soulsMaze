package com.cardclash.data.local.model

/**
 * Identificador de mazo fuertemente tipado (data class).
 *
 * Encapsula un UUID string para evitar confusion con CardId, PlayerId, etc.
 * Se genera via [DeckId.generate()] o se parsea con [DeckId.fromString()].
 *
 * NO extiende IdValue (sellado en otro package): usa composicion/patron similar.
 * La igualdad es estructural (data class) y toString devuelve el UUID.
 */
data class DeckId(val value: String) {
    init {
        require(value.isNotBlank()) { "DeckId cannot be blank" }
        // Validacion basica de formato UUID (8-4-4-4-12 hex chars)
        val uuidRegex = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        require(value.matches(uuidRegex.toRegex())) { "Invalid UUID format: $value" }
    }

    override fun toString(): String = value

    companion object {
        /** Genera un nuevo DeckId aleatorio (UUID v4). */
        fun generate(): DeckId = DeckId(java.util.UUID.randomUUID().toString())

        /**
         * Crea un DeckId desde string; lanza [IllegalArgumentException] si el
         * string esta vacio o no es un UUID valido (formato basico).
         */
        fun fromString(value: String): DeckId = DeckId(value)
    }
}