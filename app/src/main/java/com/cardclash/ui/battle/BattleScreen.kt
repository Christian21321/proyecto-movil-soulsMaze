package com.cardclash.ui.battle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardclash.di.AppContainer
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
import com.cardclash.ui.common.RarityBadge
import kotlinx.coroutines.delay
import com.cardclash.ui.theme.CardClashTheme
import com.cardclash.ui.theme.DefeatColor
import com.cardclash.ui.theme.RarityCommonColor
import com.cardclash.ui.theme.RaritySrColor
import com.cardclash.ui.theme.RaritySsrColor
import com.cardclash.ui.theme.VictoryColor

/**
 * Pantalla de combate (duelo PvP por turnos centrado en avatares).
 *
 * Compone sobre [CombatUiState] la vida prominente de ambos AVATARES, la
 * información central de turno/log, la mano de cartas hechizo/efecto y el
 * botón de fin de turno. Un banner animado anuncia visualmente cada transición
 * de turno reaccionando a [CombatUiState.isMyTurn]. Las cartas de la mano se
 * habilitan solo cuando son [HandCardUi.playable].
 */
@Composable
fun BattleScreen(
    container: AppContainer,
    onBackToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CombatViewModel =
        viewModel { CombatViewModel(container.repository, container.catalog) }
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BattleScreenContent(
            session = session,
            onPlayCard = viewModel::playCard,
            onEndTurn = viewModel::endTurn,
            onBeginTurn = viewModel::beginTurn,
            onBackToMenu = onBackToMenu,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** Contenido de la pantalla de combate (previsualizable con un estado de muestra). */
@Composable
fun BattleScreenContent(
    session: CombatSessionState,
    onPlayCard: (InstanceId, PlayerId?) -> Unit,
    onEndTurn: () -> Unit,
    onBeginTurn: () -> Unit,
    onBackToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = session.uiState
    when {
        session.status == SessionStatus.ERROR -> {
            ErrorPanel(
                message = session.lastError ?: "No se pudo iniciar la partida",
                onBackToMenu = onBackToMenu,
                modifier = modifier.fillMaxSize(),
            )
        }
        ui == null -> {
            LoadingPanel(
                status = session.status,
                modifier = modifier.fillMaxSize(),
            )
        }
        ui.isFinished -> {
            ResultPanel(
                ui = ui,
                onBackToMenu = onBackToMenu,
                modifier = modifier.fillMaxSize(),
            )
        }
        else -> {
            BattleArena(
                ui = ui,
                labels = CombatMapper.toLabels(session),
                onPlayCard = onPlayCard,
                onEndTurn = onEndTurn,
                onBeginTurn = onBeginTurn,
                modifier = modifier,
            )
        }
    }
}

/** Arena completa durante una partida en curso (avatares + mano + turnos). */
@Composable
private fun BattleArena(
    ui: CombatUiState,
    labels: CombatLabels,
    onPlayCard: (InstanceId, PlayerId?) -> Unit,
    onEndTurn: () -> Unit,
    onBeginTurn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AvatarHealthCard(
                label = "Rival",
                health = ui.opponentHeroHealth,
                maxHealth = ui.opponentHeroMaxHealth,
                active = !ui.isMyTurn,
            )
            TurnInfo(ui = ui, labels = labels)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            LogPanel(messages = ui.log)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            AvatarHealthCard(
                label = "Tu avatar",
                health = ui.myHeroHealth,
                maxHealth = ui.myHeroMaxHealth,
                active = ui.isMyTurn,
            )
            MyHand(ui = ui, onPlayCard = onPlayCard)
            Spacer(modifier = Modifier.height(4.dp))
            ActionButtons(
                ui = ui,
                onEndTurn = onEndTurn,
                onBeginTurn = onBeginTurn,
            )
        }
        TurnBanner(ui = ui)
    }
}

/**
 * Tarjeta con la vida PROMINENTE de un avatar (rival o local).
 *
 * Muestra siempre la barra de vida con progreso [health]/[maxHealth] junto al
 * valor numérico "actual / máximo". El color de acento refleja quién está en
 * turno ([active]) para reforzar el foco visual.
 */
@Composable
private fun AvatarHealthCard(
    label: String,
    health: Int,
    maxHealth: Int,
    active: Boolean,
) {
    val safeMax = if (maxHealth > 0) maxHealth else 1
    val fraction = (health.toFloat() / safeMax).coerceIn(0f, 1f)
    val accent = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val container = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Vida del avatar $label, $health de $maxHealth"
        },
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = "$health / $maxHealth",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .semantics { contentDescription = "Vida $label $health de $maxHealth" },
                color = accent,
                trackColor = MaterialTheme.colorScheme.surface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (active) "En turno" else "Esperando",
                style = MaterialTheme.typography.labelSmall,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Zona central: turno, fase, turno actual, maná y log. */
@Composable
private fun TurnInfo(ui: CombatUiState, labels: CombatLabels) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = labels.turnNumberLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = labels.phaseLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ui.isMyTurn) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.semantics { contentDescription = "Es tu turno" },
                ) {
                    Text(
                        text = "Tu turno",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        StatusChip(label = "Maná ${labels.manaLabel}")
    }
}

/** Panel de log con los últimos mensajes de la partida. */
@Composable
private fun LogPanel(messages: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Registro",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (messages.isEmpty()) {
            Text(
                text = "Sin eventos todavía",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            messages.takeLast(4).reversed().forEach { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Zona inferior: mano de cartas hechizo/efecto jugables. */
@Composable
private fun MyHand(ui: CombatUiState, onPlayCard: (InstanceId, PlayerId?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Tu mano (${ui.myHand.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (ui.myHand.isEmpty()) {
            Text(
                text = "No hay cartas en la mano",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Mano de cartas jugables"
                },
            ) {
                itemsIndexed(ui.myHand, key = { index, card -> "$index/${card.instanceId.value}" }) { _, card ->
                    HandCard(
                        card = card,
                        onTap = { if (card.playable) onPlayCard(card.instanceId, null) },
                    )
                }
            }
        }
    }
}

/** Tarjeta de carta en mano, habilitada solo si es [HandCardUi.playable]. */
@Composable
private fun HandCard(card: HandCardUi, onTap: () -> Unit) {
    val playable = card.playable
    val cardColor = if (playable) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier
            .width(132.dp)
            .semantics { contentDescription = "Carta ${card.name}, coste ${card.cost}" }
            .clickable(enabled = playable, onClick = onTap),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Coste ${card.cost}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (card.rarity != null) {
                    RarityBadge(
                        label = card.rarity.name,
                        badgeColor = rarityColor(card.rarity),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${card.attack} ATK / ${card.maxHealth} PV",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (card.isPassive) {
                Text(
                    text = "Pasiva",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (playable) "Jugar" else "No disponible",
                style = MaterialTheme.typography.labelSmall,
                color = if (playable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Banner animado que anuncia de forma IMPOSIBLE de pasar por alto la transición
 * de turno. Reacciona a [CombatUiState.isMyTurn]: aparece brevemente al cambiar
 * y se desvanece tras un retardo para no bloquear la interacción.
 */
@Composable
private fun TurnBanner(ui: CombatUiState) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(ui.isMyTurn, ui.turn) {
        visible = true
        delay(1600)
        visible = false
    }

    val isMine = ui.isMyTurn
    val bannerColor = if (isMine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val text = if (isMine) "TU TURNO" else "TURNO DEL RIVAL"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = bannerColor,
                contentColor = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.semantics { contentDescription = text },
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** Botones de acción: terminar turno (y comenzar turno según flujo). */
@Composable
private fun ActionButtons(
    ui: CombatUiState,
    onEndTurn: () -> Unit,
    onBeginTurn: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ui.canAct) {
            Button(
                onClick = onEndTurn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(text = "Terminar turno", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            OutlinedButton(
                onClick = onBeginTurn,
                enabled = !ui.canAct,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    text = if (ui.isMyTurn) "Empezar turno" else "Turno del rival…",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** Chip de estado compacto. */
@Composable
private fun StatusChip(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Estado de carga/conexión. */
@Composable
private fun LoadingPanel(status: SessionStatus, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = CombatMapper.statusLabel(status),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Pantalla final de victoria/derrota. */
@Composable
private fun ResultPanel(
    ui: CombatUiState,
    onBackToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVictory = ui.isVictory
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isVictory) VictoryColor else DefeatColor,
            contentColor = Color.White,
        ) {
            Text(
                text = if (isVictory) "¡Victoria!" else "Derrota",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isVictory) "Has ganado la partida (+5 XP)" else "Has perdido la partida (+2 XP)",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onBackToMenu,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(text = "Volver al menú", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Panel de error al iniciar la sesión. */
@Composable
private fun ErrorPanel(
    message: String,
    onBackToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onBackToMenu,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(text = "Volver al menú", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Color del badge según rareza. */
@Composable
private fun rarityColor(rarity: Rarity): Color = when (rarity) {
    Rarity.COMMON -> RarityCommonColor
    Rarity.SR -> RaritySrColor
    Rarity.SSR -> RaritySsrColor
}

// ---------------------------------------------------------------------------
// Estado de muestra para preview
// ---------------------------------------------------------------------------

private fun sampleUi(): CombatUiState = CombatUiState(
    matchId = MatchId("demo"),
    phase = MatchSnapshot.Phase.PLAYING,
    turn = 3,
    currentPlayer = PlayerId("local-player"),
    myId = PlayerId("local-player"),
    isMyTurn = true,
    myMana = 5,
    maxMana = 10,
    myHand = listOf(
        HandCardUi(
            instanceId = InstanceId("0"),
            cardId = CardId("attack-0"),
            name = "Golpe 0",
            cost = 3,
            attack = 3,
            maxHealth = 4,
            rarity = Rarity.COMMON,
            isPassive = false,
            playable = true,
        ),
        HandCardUi(
            instanceId = InstanceId("1"),
            cardId = CardId("status-frost"),
            name = "Invocar FROST",
            cost = 5,
            attack = 1,
            maxHealth = 3,
            rarity = Rarity.SSR,
            isPassive = false,
            playable = false,
        ),
    ),
    myHeroHealth = 18,
    myHeroMaxHealth = 20,
    opponentHeroHealth = 9,
    opponentHeroMaxHealth = 20,
    opponentHandSize = 3,
    opponentMana = 4,
    log = listOf(
        "Inicio del turno 3.",
        "Jugaste Golpe 0.",
        "El rival golpeó tu avatar.",
    ),
    winner = null,
    myDeckSize = 5,
    opponentDeckSize = 4,
)

@Preview(showBackground = true)
@Composable
private fun BattleScreenContentPreview() {
    CardClashTheme {
        BattleScreenContent(
            session = CombatSessionState(
                status = SessionStatus.ACTIVE,
                role = SessionRole.LOCAL_SOLO,
                uiState = sampleUi(),
            ),
            onPlayCard = { _, _ -> },
            onEndTurn = {},
            onBeginTurn = {},
            onBackToMenu = {},
        )
    }
}
