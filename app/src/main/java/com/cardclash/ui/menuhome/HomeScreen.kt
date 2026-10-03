package com.cardclash.ui.menuhome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardclash.di.AppContainer
import com.cardclash.domain.progression.Tier
import com.cardclash.ui.common.ProgressSummary
import com.cardclash.ui.theme.CardClashTheme

/**
 * Pantalla de menu: resumen de progreso + navegacion a coleccion, historial y mazos.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenCollection: () -> Unit,
    onOpenHistory: () -> Unit,
    onPlayCombat: () -> Unit,
    onOpenDeckBuilder: (String?) -> Unit, // deckId opcional para editar
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel { HomeViewModel(container.repository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            HomeContent(
                state = state,
                onOpenCollection = onOpenCollection,
                onOpenHistory = onOpenHistory,
                onPlayCombat = onPlayCombat,
                onOpenDeckBuilder = onOpenDeckBuilder,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** Contenido estatico del menu (previsualizable con un estado de muestra). */
@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenCollection: () -> Unit,
    onOpenHistory: () -> Unit,
    onPlayCombat: () -> Unit,
    onOpenDeckBuilder: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "CardClash",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Menu principal",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProgressSummary(
                    level = state.level,
                    tier = state.tier,
                    xpTotal = state.xpTotal,
                    xpIntoLevel = state.xpIntoLevel,
                    xpNeededForNextLevel = state.xpNeededForNextLevel,
                    progressFraction = state.xpProgressFraction,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Cartas en coleccion: ${state.collectionSize} (${state.totalCopies} copias)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onPlayCombat,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(text = "Jugar · Combate local", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onOpenDeckBuilder(null) }, // Nuevo mazo
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(text = "Mazos", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onOpenCollection,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(text = "Coleccion", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpenHistory,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(text = "Historial", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    CardClashTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                level = 42,
                tier = Tier.T2,
                xpTotal = 4250,
                xpIntoLevel = 100,
                xpNeededForNextLevel = 4300,
                xpProgressFraction = 0.67f,
                collectionSize = 7,
                totalCopies = 12,
            ),
            onOpenCollection = {},
            onOpenHistory = {},
            onPlayCombat = {},
            onOpenDeckBuilder = {},
        )
    }
}