package com.cardclash.data.remote.sync

/**
 * Constantes de los nombres de campo del documento `actions/{autoId}` (ADR-009).
 */
object ActionFields {
    const val PLAYER_ID = "playerId"
    const val ACTION_TYPE = "actionType"
    const val SEQUENCE = "sequence"
    const val INSTANCE_ID = "instanceId"
    const val TARGET_PLAYER = "targetPlayer"
    const val TIMESTAMP = "timestamp"
    const val STATUS = "status" // PENDING / PROCESSED / REJECTED
    const val RESULT = "result"
}
