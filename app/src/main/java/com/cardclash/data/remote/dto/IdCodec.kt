package com.cardclash.data.remote.dto

import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.IdValue
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.StatusInstanceId

/**
 * Codificacion/decodificacion de los identificadores [IdValue] del dominio.
 *
 * En el wire, todo [IdValue] se representa por su [IdValue.value] (String plano).
 * Para reconstruir el tipo fuertemente tipado se usa [rehydrate], que mapea la
 * clase destino a su constructor de un unico String.
 */
internal object IdCodec {

    /** Devuelve el valor plano de un [IdValue] (o null si este lo es). */
    fun value(id: IdValue?): String? = id?.value

    /**
     * Reconstruye de forma generica un [IdValue] a partir de su valor plano.
     *
     * Devuelve null cuando [value] es null; lanza si el tipo solicitado ([T])
     * no es uno de los [IdValue] conocidos del dominio.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : IdValue> rehydrate(value: String?): T? {
        if (value == null) return null
        return when (T::class) {
            CardId::class -> CardId(value)
            InstanceId::class -> InstanceId(value)
            StatusInstanceId::class -> StatusInstanceId(value)
            PlayerId::class -> PlayerId(value)
            MatchId::class -> MatchId(value)
            else -> error("Tipo de IdValue no soportado en rehydrate: ${T::class}")
        } as T
    }
}
