package com.cardclash.domain.battle

/**
 * Rol del jugador local en una sesión de combate.
 */
enum class SessionRole {
    /** Anfitrión de una partida en línea (posee el motor y procesa la cola). */
    HOST,

    /** Cliente que se unió a una partida en línea (recibe proyecciones). */
    CLIENT,

    /** Partida local/demo: ambos lados se simulan en el mismo proceso. */
    LOCAL_SOLO,
}

/**
 * Ciclo de vida de la sesión de combate tal como lo observa la UI.
 */
enum class SessionStatus {
    /** Sin partida activa (estado inicial o tras [MatchSessionController.leaveMatch]). */
    IDLE,

    /** Creando/uniéndose a la partida en línea. */
    CONNECTING,

    /** Partida en curso (turnos jugables). */
    ACTIVE,

    /** Partida terminada ([CombatUiState.isFinished]). */
    FINISHED,

    /** Error al iniciar la sesión (p. ej. no se pudo crear/crear match). */
    ERROR,
}

/**
 * Estado externo expuesto por [MatchSessionController.state] como flujo inmutable.
 *
 * Envuelve el [CombatUiState] vigente (o null mientras se conecta) junto con el
 * rol y el ciclo de vida, para que la UI cree el ViewModel sobre una única fuente.
 */
data class CombatSessionState(
    val status: SessionStatus,
    val role: SessionRole? = null,
    val uiState: CombatUiState? = null,
    /** Motivos de rechazo recientes (acciones ilegales/duplicadas/fuera de orden). */
    val rejectionLog: List<String> = emptyList(),
    val lastError: String? = null,
) {
    /** true si la partida ha terminado. */
    val isFinished: Boolean get() = status == SessionStatus.FINISHED

    /** true si hay una vista válida disponible. */
    val hasUi: Boolean get() = uiState != null
}
