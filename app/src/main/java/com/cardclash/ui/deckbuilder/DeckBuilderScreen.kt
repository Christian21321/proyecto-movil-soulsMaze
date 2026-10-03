@file:OptIn(ExperimentalMaterial3Api::class)

package com.cardclash.ui.deckbuilder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cardclash.di.AppContainer
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.Rarity
import com.cardclash.ui.common.RarityBadge
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CardTypeFilter
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CollectionCardUi
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.DeckSlotUi
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.Editing
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.FilterState
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ManaCurveData
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ValidationResult
import com.cardclash.ui.theme.CardClashTheme
import com.cardclash.ui.theme.RarityCommonColor
import com.cardclash.ui.theme.RaritySrColor
import com.cardclash.ui.theme.RaritySsrColor

/**
 * Pantalla del constructor de mazos: nombre, 9 huecos (8 normales + 1 pasiva),
 * validación, curva de mana y colección filtrable. Tocar una carta de la
 * colección la añade; tocar un hueco ocupado la quita.
 */
@Composable
fun DeckBuilderScreen(
    container: AppContainer,
    navController: NavController,
    deckId: String? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: DeckBuilderViewModel = viewModel {
        DeckBuilderViewModel(container.repository, container.catalog, deckId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state) {
        if (state is DeckBuilderUiState.Saved) navController.popBackStack()
    }

    val onBack = { if (viewModel.onBackPress()) navController.popBackStack() }
    BackHandler(onBack = { onBack() })

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Constructor de mazos") },
                navigationIcon = { TextButton(onClick = { onBack() }) { Text("Volver") } },
                actions = {
                    val editing = state as? Editing
                    TextButton(
                        onClick = viewModel::onSave,
                        enabled = editing != null && !editing.isSaving && editing.validation.isValid,
                    ) {
                        Text("Guardar")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val current = state) {
            DeckBuilderUiState.Loading, DeckBuilderUiState.Saved -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is DeckBuilderUiState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::load) { Text("Reintentar") }
                }
            }

            is Editing -> {
                DeckBuilderContent(
                    state = current,
                    onNameChange = viewModel::onNameChange,
                    onSlotClick = viewModel::onRemoveSlot,
                    onCardClick = viewModel::onAddCard,
                    onToggleType = viewModel::onToggleType,
                    onOnlyOwnedChange = viewModel::onOnlyOwnedChange,
                    modifier = Modifier.padding(innerPadding),
                )
                if (current.confirmDiscard) {
                    AlertDialog(
                        onDismissRequest = viewModel::onDismissDiscard,
                        title = { Text("¿Salir sin guardar?") },
                        text = { Text("Perderás los cambios de este mazo.") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.onDismissDiscard()
                                navController.popBackStack()
                            }) { Text("Salir") }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::onDismissDiscard) { Text("Seguir editando") }
                        },
                    )
                }
            }
        }
    }
}

/** Contenido del editor (previsualizable con un estado de muestra). */
@Composable
fun DeckBuilderContent(
    state: Editing,
    onNameChange: (String) -> Unit,
    onSlotClick: (Int) -> Unit,
    onCardClick: (CardId) -> Unit,
    onToggleType: (CardTypeFilter) -> Unit,
    onOnlyOwnedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.deck.name,
                onValueChange = onNameChange,
                label = { Text("Nombre del mazo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = "Cartas ${state.deck.nonPassiveCount}/${DeckBuilderUiState.NON_PASSIVE_SLOTS}" +
                    " · Pasiva ${state.deck.passiveCount}/1",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SlotGrid(slots = state.deck.slots, onSlotClick = onSlotClick)
        }
        item { ValidationCard(state.validation) }
        item { ManaCurve(state.manaCurve) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
        item {
            Filters(filter = state.filter, onToggleType = onToggleType, onOnlyOwnedChange = onOnlyOwnedChange)
        }
        if (state.collection.isEmpty()) {
            item {
                Text(
                    text = "No hay cartas con estos filtros.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items = state.collection, key = { it.cardId.value }) { card ->
            CollectionCardRow(card = card, onClick = { onCardClick(card.cardId) })
        }
    }
}

/** Huecos del mazo en una cuadrícula de 3x3; el último es el de la pasiva. */
@Composable
private fun SlotGrid(slots: List<DeckSlotUi>, onSlotClick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { slot ->
                    SlotCell(
                        slot = slot,
                        onClick = { onSlotClick(slot.index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotCell(slot: DeckSlotUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        slot.hasError -> MaterialTheme.colorScheme.error
        slot.isPassiveSlot -> RaritySrColor
        else -> MaterialTheme.colorScheme.outline
    }
    val description = if (slot.isEmpty) {
        if (slot.isPassiveSlot) "Hueco de pasiva vacío" else "Hueco ${slot.index + 1} vacío"
    } else {
        "${slot.name}, toca para quitarla"
    }
    Box(
        modifier = modifier
            .height(72.dp)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = shape)
            .clickable(enabled = !slot.isEmpty, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (slot.isEmpty) {
            Text(
                text = if (slot.isPassiveSlot) "Pasiva" else "${slot.index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = slot.name.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                slot.manaCost?.let {
                    Text(
                        text = "Mana $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationCard(validation: ValidationResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (validation.isValid) {
                Text(
                    text = "Mazo válido",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = "Mazo no válido",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                validation.errors.forEach { error ->
                    Text(text = "· $error", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Barras con el número de cartas por coste de mana. */
@Composable
private fun ManaCurve(curve: ManaCurveData) {
    Column {
        Text("Curva de mana", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        val costs = (curve.byCost.keys + (1..5)).sorted().distinct()
        val maxCount = curve.maxCount.coerceAtLeast(1)
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            costs.forEach { cost ->
                val count = curve.byCost[cost] ?: 0
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$count", style = MaterialTheme.typography.labelSmall)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((40 * count / maxCount).dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            ),
                    )
                    Text("$cost", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun Filters(
    filter: FilterState,
    onToggleType: (CardTypeFilter) -> Unit,
    onOnlyOwnedChange: (Boolean) -> Unit,
) {
    Column {
        Text("Colección", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        CardTypeFilter.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { type ->
                    FilterChip(
                        selected = type in filter.types,
                        onClick = { onToggleType(type) },
                        label = { Text(type.label) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Solo cartas poseídas", modifier = Modifier.weight(1f))
            Switch(checked = filter.onlyOwned, onCheckedChange = onOnlyOwnedChange)
        }
    }
}

@Composable
private fun CollectionCardRow(card: CollectionCardUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = card.canAdd, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (card.copiesOwned > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RarityBadge(label = card.rarity.name, badgeColor = rarityColor(card.rarity))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mana ${card.manaCost} · ${card.effectDescription}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = when {
                    card.inDeck -> "En mazo"
                    card.copiesOwned == 0 -> "No poseída"
                    card.canAdd -> "Añadir"
                    else -> "Sin hueco"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (card.canAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Color semántico del badge según rareza. */
private fun rarityColor(rarity: Rarity): Color = when (rarity) {
    Rarity.COMMON -> RarityCommonColor
    Rarity.SR -> RaritySrColor
    Rarity.SSR -> RaritySsrColor
}

@Preview(showBackground = true)
@Composable
private fun DeckBuilderContentPreview() {
    val deck = DeckBuilderMapper.emptyDeck()
    CardClashTheme {
        DeckBuilderContent(
            state = Editing(
                deck = deck,
                collection = listOf(
                    CollectionCardUi(
                        cardId = CardId("attack-0"),
                        name = "Golpe 0",
                        rarity = Rarity.COMMON,
                        manaCost = 3,
                        type = CardTypeFilter.ATTACK,
                        copiesOwned = 2,
                        isPassive = false,
                        effectDescription = "Daño 4",
                        inDeck = false,
                        canAdd = true,
                    ),
                ),
                filter = FilterState(),
                validation = ValidationResult(errors = listOf("Añade al menos una carta."), slotsWithErrors = emptySet()),
                manaCurve = ManaCurveData(emptyMap()),
            ),
            onNameChange = {},
            onSlotClick = {},
            onCardClick = {},
            onToggleType = {},
            onOnlyOwnedChange = {},
        )
    }
}
