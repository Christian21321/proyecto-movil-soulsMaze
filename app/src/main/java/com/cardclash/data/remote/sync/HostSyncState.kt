package com.cardclash.data.remote.sync

import com.cardclash.domain.model.MatchSnapshot

/**
 * Estado interno e inmutable del host para la sincronizacion.
 *
 * Engrana el ultimo snapshot validado por el motor, la secuencia que el host
 * espera procesar a continuacion ([nextExpectedActionSeq]) y la version
 * compartida de las particiones `stateHost`/`stateClient` (ADR-009).
 */
data class HostSyncState(
    val snapshot: MatchSnapshot,
    val nextExpectedActionSeq: Long,
    val version: Long,
)
