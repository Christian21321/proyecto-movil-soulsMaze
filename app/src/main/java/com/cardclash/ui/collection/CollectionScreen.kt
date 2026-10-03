package com.cardclash.ui.collection

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardclash.di.AppContainer
import com.cardclash.domain.model.Rarity
import com.cardclash.domain.progression.Tier
import com.cardclash.ui.common.ProgressSummary
import com.cardclash.ui.common.RarityBadge
import com.cardclash.ui.theme.CardClashTheme
import com.cardclash.ui.theme.RarityCommonColor
import com.cardclash.ui.theme.RaritySrColor
import com.cardclash.ui.theme.RaritySsrColor

/**
 * Pantalla de coleccion + progresion: cabecera con nivel/tramo/XP y lista de
 * cartas del catalogo fusionadas con copias poseidas y limite del tramo.
 */
@Composable
fun CollectionScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val viewModel: CollectionViewModel =
        viewModel { CollectionViewModel(container.repository, container.catalog) }
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
            CollectionContent(
                state = state,
                onAddCopy = viewModel::addCopy,
                onRemoveCopy = viewModel::removeCopy,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** Contenido estatico de coleccion (previsualizable con un estado de muestra). */
@Composable
fun CollectionContent(
    state: CollectionUiState,
    onAddCopy: (com.cardclash.domain.model.CardId) -> Unit,
    onRemoveCopy: (com.cardclash.domain.model.CardId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = "Coleccion",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Progresion y cartas poseidas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            ProgressSummary(
                level = state.level,
                tier = state.tier,
                xpTotal = state.xpTotal,
                xpIntoLevel = state.xpIntoLevel,
                xpNeededForNextLevel = state.xpNeededForNextLevel,
                progressFraction = state.xpProgressFraction,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Limite de copias por carta: ${state.maxCopiesAllowed}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 24.dp,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = state.cards, key = { it.cardId.value }) { card ->
                CollectionCardRow(
                    card = card,
                    onAdd = { onAddCopy(card.cardId) },
                    onRemove = { onRemoveCopy(card.cardId) },
                )
            }
        }
    }
}

/** Fila de carta: nombre, badge de rareza, coste de mana y copias vs limite. */
@Composable
private fun CollectionCardRow(
    card: CollectionCardUi,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RarityBadge(
                        label = card.rarity.name,
                        badgeColor = rarityColor(card.rarity),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mana ${card.manaCost}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${card.copiesOwned}/${card.maxCopies}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 4.dp),
            )
            IconButton(
                onClick = onRemove,
                enabled = card.canRemove,
                modifier = Modifier.semantics {
                    contentDescription = "Eliminar copia de ${card.name}"
                },
            ) {
                Text(
                    text = "\u2212",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (card.canRemove) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onAdd,
                enabled = card.canAdd,
                modifier = Modifier.semantics {
                    contentDescription = "Anadir copia de ${card.name}"
                },
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (card.canAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Color semantico del badge segun rareza. */
@Composable
private fun rarityColor(rarity: Rarity): androidx.compose.ui.graphics.Color = when (rarity) {
    Rarity.COMMON -> RarityCommonColor
    Rarity.SR -> RaritySrColor
    Rarity.SSR -> RaritySsrColor
}

@Preview(showBackground = true)
@Composable
private fun CollectionContentPreview() {
    CardClashTheme {
        CollectionContent(
            state = CollectionUiState(
                isLoading = false,
                level = 42,
                tier = Tier.T2,
                xpTotal = 4250,
                xpIntoLevel = 100,
                xpNeededForNextLevel = 4300,
                xpProgressFraction = 0.67f,
                maxCopiesAllowed = 3,
                cards = listOf(
                    CollectionCardUi(
                        cardId = com.cardclash.domain.model.CardId("attack-0"),
                        name = "Golpe 0",
                        rarity = Rarity.COMMON,
                        manaCost = 3,
                        copiesOwned = 2,
                        maxCopies = 3,
                        canAdd = true,
                        canRemove = true,
                    ),
                    CollectionCardUi(
                        cardId = com.cardclash.domain.model.CardId("passive-max_mana-+2"),
                        name = "Aura de MAX_MANA +2",
                        rarity = Rarity.SR,
                        manaCost = 4,
                        copiesOwned = 0,
                        maxCopies = 3,
                        canAdd = true,
                        canRemove = false,
                    ),
                    CollectionCardUi(
                        cardId = com.cardclash.domain.model.CardId("status-frost"),
                        name = "Invocar FROST",
                        rarity = Rarity.SSR,
                        manaCost = 5,
                        copiesOwned = 3,
                        maxCopies = 3,
                        canAdd = false,
                        canRemove = true,
                    ),
                ),
            ),
            onAddCopy = {},
            onRemoveCopy = {},
        )
    }
}