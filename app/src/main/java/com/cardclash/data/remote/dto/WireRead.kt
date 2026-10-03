package com.cardclash.data.remote.dto

/**
 * Helpers de lectura tipada sobre un mapa wire (Firestore).
 *
 * Firestore serializa los numeros como [Long], de modo que los campos de tipo
 * [Int] del dominio se leen como numero ([Number]) y se convierten con [Number.toInt].
 * Estos helpers centralizan esa conversion y producen errores claros cuando un
 * campo requerido falta o tiene un tipo inesperado.
 */
internal object WireRead {

    fun str(map: Map<String, Any?>, key: String): String? =
        map[key] as? String

    fun requireStr(map: Map<String, Any?>, key: String): String =
        str(map, key) ?: error("Campo '$key' ausente o no es String en el wire.")

    fun int(map: Map<String, Any?>, key: String): Int =
        (map[key] as? Number)?.toInt()
            ?: error("Campo '$key' ausente o no es numero en el wire.")

    fun long(map: Map<String, Any?>, key: String): Long =
        (map[key] as? Number)?.toLong()
            ?: error("Campo '$key' ausente o no es numero en el wire.")

    fun bool(map: Map<String, Any?>, key: String, default: Boolean = false): Boolean =
        map[key] as? Boolean ?: default

    fun list(map: Map<String, Any?>, key: String): List<Any?> =
        (map[key] as? List<Any?>) ?: error("Campo '$key' ausente o no es List en el wire.")

    /** Lee uno de los mapas del dominio (manas, hands, boards, ...). */
    @Suppress("UNCHECKED_CAST")
    fun stringMap(map: Map<String, Any?>, key: String): Map<String, Any?> =
        (map[key] as? Map<String, Any?>)
            ?: error("Campo '$key' ausente o no es Map en el wire.")
}
