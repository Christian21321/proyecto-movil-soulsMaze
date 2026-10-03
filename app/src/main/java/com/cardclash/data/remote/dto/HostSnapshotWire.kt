package com.cardclash.data.remote.dto

import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.data.remote.dto.WireFields.MATCH_ID
import com.cardclash.data.remote.dto.WireFields.PROCESSED_ACTION_SEQ
import com.cardclash.data.remote.dto.WireFields.VERSION

/**
 * Envoltorio del documento `stateHost` de Firestore (ADR-009).
 *
 * Combina el [MatchSnapshot] completo con los metadatos de control ([version] y
 * [processedActionSeq]) que NO forman parte del snapshot. [snapshot] puede ser
 * null cuando el documento aun no contiene estado materializado.
 */
data class HostSnapshotWire(
    val version: Long,
    val processedActionSeq: Long,
    val snapshot: MatchSnapshot?,
) {

    /**
     * Produce el mapa wire completo del documento `stateHost`: todos los campos
     * del snapshot mas los metadatos de control (planos en el mismo documento).
     */
    fun toWire(): Map<String, Any?> {
        val snapshotWire = snapshot?.let { MatchSnapshotSerializer.toWire(it) }.orEmpty()
        return snapshotWire + mapOf(
            VERSION to version,
            PROCESSED_ACTION_SEQ to processedActionSeq,
        )
    }

    companion object {

        /** Reconstruye el envoltorio desde el wire. */
        fun fromWire(map: Map<String, Any?>): HostSnapshotWire = HostSnapshotWire(
            version = WireRead.long(map, VERSION),
            processedActionSeq = WireRead.long(map, PROCESSED_ACTION_SEQ),
            snapshot = if (map.containsKey(MATCH_ID)) {
                MatchSnapshotDeserializer.fromWire(map)
            } else {
                null
            },
        )
    }
}
