package com.cardclash.domain.engine

/**
 * Abstracción de aleatoriedad para el motor de combate.
 *
 * La inyección de aleatoriedad por interfaz es OBLIGATORIA: el motor NUNCA crea
 * un `Random` interno. Así, en producción se usa [KotlinRandomDiceRoller] y en
 * tests [SeededDiceRoller] (determinista) para reproducir exactamente qué se
 * lanza.
 */
interface DiceRoller {

    /**
     * Devuelve un entero aleatorio en el rango inclusivo [min]..[max].
     * Precondición: [min] <= [max].
     */
    fun nextInt(min: Int, max: Int): Int

    /** Conveniencia: lanza un dado de [sides] caras (1..[sides]). */
    fun d(sides: Int): Int = nextInt(1, sides)
}

/**
 * Implementación de producción basada en `kotlin.random.Random`.
 *
 * Usa la fuente de entropía del sistema; sus resultados no son reproducibles,
 * por lo que es la opción adecuada para partidas reales.
 */
class KotlinRandomDiceRoller(
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
) : DiceRoller {
    override fun nextInt(min: Int, max: Int): Int {
        require(min <= max) { "min ($min) debe ser <= max ($max)" }
        return random.nextInt(min, max + 1)
    }
}

/**
 * Implementación determinista alimentada por una semilla fija.
 *
 * Pensada EXCLUSIVAMENTE para tests: si se construye con la misma semilla, la
 * secuencia de enteros generada es siempre idéntica, lo que permite escribir
 * pruebas reproducibles y predecibles sobre las reglas del motor.
 */
class SeededDiceRoller(
    private val seed: Long,
) : DiceRoller {
    private val random: kotlin.random.Random = kotlin.random.Random(seed)

    override fun nextInt(min: Int, max: Int): Int {
        require(min <= max) { "min ($min) debe ser <= max ($max)" }
        return random.nextInt(min, max + 1)
    }
}
