package com.cardclash.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardclash.di.AppContainer
import com.cardclash.ui.theme.CardClashTheme
import com.cardclash.ui.theme.DefeatColor
import com.cardclash.ui.theme.VictoryColor

/**
 * Pantalla de historial: resumen de record (victorias/derrotas/XP) y lista de
 * partidas registradas (mas recientes primero).
 */
@Composable
fun HistoryScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val viewModel: HistoryViewModel = viewModel { HistoryViewModel(container.repository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            HistoryContent(
                state = state,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** Contenido estatico del historial (previsualizable con un estado de muestra). */
@Composable
fun HistoryContent(
    state: HistoryUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = "Historial",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Partidas registradas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RecordStat(label = "Victorias", value = state.wins, color = VictoryColor, modifier = Modifier.weight(1f))
                RecordStat(label = "Derrotas", value = state.losses, color = DefeatColor, modifier = Modifier.weight(1f))
                RecordStat(label = "XP ganado", value = state.totalXpEarned, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        if (state.entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Sin partidas registradas todavia",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = state.entries, key = { it.matchId }) { entry ->
                    HistoryEntryRow(entry = entry)
                }
            }
        }
    }
}

/** Estadistica compacta de la cabecera del historial. */
@Composable
private fun RecordStat(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fila de partida: resultado, XP ganado, fecha formateada y resumen opcional. */
@Composable
private fun HistoryEntryRow(
    entry: HistoryEntryUi,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (entry.victory) "Victoria" else "Derrota",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (entry.victory) VictoryColor else DefeatColor,
                )
                Text(
                    text = entry.playedAtLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!entry.summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "+${entry.xpEarned} XP",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryContentPreview() {
    CardClashTheme {
        HistoryContent(
            state = HistoryUiState(
                isLoading = false,
                entries = listOf(
                    HistoryEntryUi("m1", victory = true, xpEarned = 5, playedAtLabel = "28/08/2026 14:32", summary = "Victoria por KO"),
                    HistoryEntryUi("m2", victory = false, xpEarned = 2, playedAtLabel = "28/08/2026 13:10", summary = null),
                    HistoryEntryUi("m3", victory = true, xpEarned = 5, playedAtLabel = "27/08/2026 21:05", summary = null),
                ),
                wins = 2,
                losses = 1,
                totalXpEarned = 12,
            ),
        )
    }
}