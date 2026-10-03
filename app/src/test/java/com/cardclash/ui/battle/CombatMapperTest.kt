package com.cardclash.ui.battle

import com.cardclash.domain.battle.CombatSessionState
import com.cardclash.domain.battle.CombatUiState
import com.cardclash.domain.battle.HandCardUi
import com.cardclash.domain.battle.SessionRole
import com.cardclash.domain.battle.SessionStatus
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.InstanceId
import com.cardclash.domain.model.MatchId
import com.cardclash.domain.model.MatchSnapshot
import com.cardclash.domain.model.PlayerId
import com.cardclash.domain.model.Rarity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests JVM (JUnit 4) del mapper PURO [CombatMapper]: derivación de etiquetas
 * de la pantalla de combate desde un [CombatSessionState]. El mapper NO expone
 * etiquetas de vida de avatar (los tableros fueron eliminados en Fase 1); sus
 * etiquetas cubren turno, fase, mana y conteo de mano rival.
 */
class CombatMapperTest {

    private val myId = PlayerId("P1")
    private val oppId = PlayerId("P2")

    private fun ui(
        isMyTurn: Boolean,
        phase: MatchSnapshot.Phase = MatchSnapshot.Phase.PLAYING,
        myMana: Int = 5,
        maxMana: Int = 10,
        opponentHandSize: Int = 3,
    ): CombatUiState = CombatUiState(
        matchId = MatchId("m1"),
        phase = phase,
        turn = 3,
        currentPlayer = if (isMyTurn) myId else oppId,
        myId = myId,
        isMyTurn = isMyTurn,
        myMana = myMana,
        maxMana = maxMana,
        myHand = emptyList(),
        myHeroHealth = 22,
        myHeroMaxHealth = 30,
        opponentHeroHealth = 7,
        opponentHeroMaxHealth = 30,
        opponentHandSize = opponentHandSize,
        opponentMana = 4,
        log = emptyList(),
        winner = null,
        myDeckSize = 5,
        opponentDeckSize = 4,
    )

    @Test
    fun turnLabel_distingueMiTurnoDelRival() {
        assertEquals("Tu turno", CombatMapper.turnLabel(ui(isMyTurn = true)))
        assertEquals("Turno del rival", CombatMapper.turnLabel(ui(isMyTurn = false)))
    }

    @Test
    fun phaseLabel_mapeaLasTresFases() {
        assertEquals("Preparación", CombatMapper.phaseLabel(MatchSnapshot.Phase.PREPARING))
        assertEquals("En curso", CombatMapper.phaseLabel(MatchSnapshot.Phase.PLAYING))
        assertEquals("Finalizada", CombatMapper.phaseLabel(MatchSnapshot.Phase.FINISHED))
    }

    @Test
    fun manaYConteo_seFormateanComoTexto() {
        assertEquals("5 / 10", CombatMapper.manaLabel(5, 10))
        assertEquals("Cartas en mano: 3", CombatMapper.opponentHandLabel(3))
        assertEquals("Turno 3", CombatMapper.turnNumberLabel(3))
    }

    @Test
    fun toLabels_incluyePresentacionDerivadaDeLaSesion() {
        val labels = CombatMapper.toLabels(
            CombatSessionState(
                status = SessionStatus.ACTIVE,
                role = SessionRole.LOCAL_SOLO,
                uiState = ui(isMyTurn = true),
            ),
        )
        assertEquals("En curso", labels.phaseLabel)
        assertEquals("Tu turno", labels.turnLabel)
        assertEquals("Turno 3", labels.turnNumberLabel)
        assertEquals("5 / 10", labels.manaLabel)
        assertEquals("Cartas en mano: 3", labels.opponentHandLabel)
        assertEquals("En partida", labels.statusLabel)
        assertEquals("Local", labels.roleLabel)
    }

    @Test
    fun toLabels_sinVista_usaLosValoresDeEstado() {
        val labels = CombatMapper.toLabels(
            CombatSessionState(status = SessionStatus.CONNECTING, role = SessionRole.HOST, uiState = null),
        )
        assertEquals("Conectando…", labels.turnLabel)
        assertEquals("—", labels.phaseLabel)
        assertEquals("—", labels.manaLabel)
        assertEquals("Anfitrión", labels.roleLabel)
    }
}
