package com.cardclash.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.spacedBy
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetState
import androidx.compose.material3.BottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.CustomAction
import androidx.compose.ui.semantics.CustomActions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardclash.domain.model.Rarity
import com.cardclash.ui.common.RarityBadge
import com.cardclash.ui.theme.CardClashTheme
import com.cardclash.ui.theme.RarityCommonColor
import com.cardclash.ui.theme.RarityOnBadgeColor
import com.cardclash.ui.theme.RaritySrColor
import com.cardclash.ui.theme.RaritySsrColor
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxHeight
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.FilterState
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CollectionCardUi
import com.cardclash.ui.deckbuilder.FiltersRow
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CardTypeFilter
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CopyFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionBottomSheet(
    isExpanded: Boolean,
    onExpandChanged: (Boolean) -> Unit,
    filter: FilterState,
    onFilterChange: (FilterState) -> Unit,
    collection: List<CollectionCardUi>,
    onCardAdd: (CollectionCardUi) -> Unit,
    onCardDetail: (CollectionCardUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomSheetState = remember {
        BottomSheetState(
            initialValue = if (isExpanded) BottomSheetValue.Expanded else BottomSheetValue.Collapsed,
            confirmStateChange = { value ->
                when (value) {
                    BottomSheetValue.Collapsed, BottomSheetValue.HalfExpanded, BottomSheetValue.Expanded -> true
                    else -> false
                }
            },
            skipHalfExpanded = false,
            skipCollapsed = false
        )
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(isExpanded) {
        val targetValue = if (isExpanded) BottomSheetValue.Expanded else BottomSheetValue.Collapsed
        if (bottomSheetState.currentValue != targetValue) {
            scope.launch {
                bottomSheetState.animateTo(targetValue)
            }
        }
    }

    val currentSheetValue = bottomSheetState.currentValue
    val isSheetExpanded = derivedStateOf { currentSheetValue == BottomSheetValue.Expanded }
    val isSheetHalfExpanded = derivedStateOf { currentSheetValue == BottomSheetValue.HalfExpanded }
    val isSheetCollapsed = derivedStateOf { currentSheetValue == BottomSheetValue.Collapsed }

    BottomSheetScaffold(
        sheetState = bottomSheetState,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetPeekHeight = 120.dp,
        sheetContent = {
            CollectionSheetContent(
                filter = filter,
                onFilterChange = onFilterChange,
                collection = collection,
                onCardAdd = onCardAdd,
                onCardDetail = onCardDetail,
                isCollapsed = isSheetCollapsed.value,
                isHalfExpanded = isSheetHalfExpanded.value,
                isExpanded = isSheetExpanded.value,
                onExpandChanged = { expanded ->
                    scope.launch {
                        bottomSheetState.animateTo(if (expanded) BottomSheetValue.Expanded else BottomSheetValue.Collapsed)
                    }
                    onExpandChanged(expanded)
                },
                modifier = modifier
            )
        },
        sheetDragHandle = {
            SheetDragHandle(
                isExpanded = isSheetExpanded.value,
                isHalfExpanded = isSheetHalfExpanded.value,
                onClick = {
                    scope.launch {
                        val nextValue = when (currentSheetValue) {
                            BottomSheetValue.Collapsed -> BottomSheetValue.HalfExpanded
                            BottomSheetValue.HalfExpanded -> BottomSheetValue.Expanded
                            BottomSheetValue.Expanded -> BottomSheetValue.Collapsed
                            else -> BottomSheetValue.Collapsed
                        }
                        bottomSheetState.animateTo(nextValue)
                        onExpandChanged(nextValue == BottomSheetValue.Expanded || nextValue == BottomSheetValue.HalfExpanded)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionSheetContent(
    filter: FilterState,
    onFilterChange: (FilterState) -> Unit,
    collection: List<CollectionCardUi>,
    onCardAdd: (CollectionCardUi) -> Unit,
    onCardDetail: (CollectionCardUi) -> Unit,
    isCollapsed: Boolean,
    isHalfExpanded: Boolean,
    isExpanded: Boolean,
    onExpandChanged: (Boolean) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics {
                role = Role.Region
                heading()
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Colección de cartas"
            }
    ) {
        FiltersRow(
            filter = filter,
            onTypeFilterChange = { type ->
                onFilterChange(filter.copy(types = if (filter.types.contains(type)) {
                    filter.types.filter { it != type }
                } else {
                    filter.types + type
                }))
            },
            onRarityFilterChange = { rarity ->
                onFilterChange(filter.copy(rarities = if (filter.rarities.contains(rarity)) {
                    filter.rarities.filter { it != rarity }
                } else {
                    filter.rarities + rarity
                }))
            },
            onManaCostFilterChange = { cost ->
                onFilterChange(filter.copy(manaCosts = if (filter.manaCosts.contains(cost)) {
                    filter.manaCosts.filter { it != cost }
                } else {
                    filter.manaCosts + cost
                }))
            },
            onCopyFilterChange = { copyFilter ->
                onFilterChange(filter.copy(copyFilter = copyFilter))
            },
            onSearchChange = { query ->
                // Search handled by parent via filter state
            },
            onOnlyOwnedChange = { onlyOwned ->
                onFilterChange(filter.copy(onlyOwned = onlyOwned))
            },
            onOnlyCompatibleChange = { onlyCompatible ->
                onFilterChange(filter.copy(onlyCompatible = onlyCompatible))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .animateContentSize()
        )

        if (!isCollapsed) {
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            val sortedCards = collection
                .sortedWith(
                    compareBy<CollectionCardUi> { it.manaCost }
                        .thenBy { it.rarity.ordinal }
                        .thenBy { it.name }
                )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp)
                    .semantics {
                        contentDescription = "Lista de ${sortedCards.size} cartas disponibles"
                    },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedCards) { card ->
                    CollectionCardItem(
                        card = card,
                        onAdd = { onCardAdd(card) },
                        onDetail = { onCardDetail(card) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetDragHandle(
    isExpanded: Boolean,
    isHalfExpanded: Boolean,
    onClick: () -> Unit
) {
    val rotation = if (isExpanded) 180f else 0f
    val stateDesc = when {
        isExpanded -> "Panel colección, 90% expandido"
        isHalfExpanded -> "Panel colección, 50% expandido"
        else -> "Panel colección, colapsado"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface)
            .semantics {
                role = Role.Button
                this.stateDescription = stateDesc
                contentDescription = stateDesc
                customActions = CustomActions(
                    CustomAction("Expandir panel") { true },
                    CustomAction("Colapsar panel") { true }
                )
            }
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp, 4.dp)
                .graphicsLayer {
                    this.rotationZ = rotation
                }
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}

@Composable
fun CollectionCardItem(
    card: CollectionCardUi,
    onAdd: () -> Unit,
    onDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rarityColor = when (card.rarity) {
        Rarity.COMMON -> RarityCommonColor
        Rarity.SR -> RaritySrColor
        Rarity.SSR -> RaritySsrColor
    }

    val buttonText = when {
        !card.canAdd -> "Máx"
        !card.isCompatible -> "Bloqueado"
        else -> "+"
    }

    val buttonEnabled = card.canAdd && card.isCompatible

    val contentDesc = "${card.name}, ${card.rarity.name}, coste ${card.manaCost}, ${card.copiesOwned} de ${card.maxCopies} copias${if (!card.canAdd) ", límite alcanzado" else ""}${if (!card.isCompatible) ", incompatible con el héroe" else ""}"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                this.contentDescription = contentDesc
                customActions = CustomActions(
                    CustomAction("Ver detalles") { onDetail(); true }
                )
            }
            .combinedClickable(
                onClick = onAdd,
                onLongClick = onDetail
            )
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    RarityBadge(
                        label = card.rarity.name,
                        badgeColor = rarityColor,
                        modifier = Modifier.height(20.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${card.manaCost}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "${card.copiesOwned}/${card.maxCopies}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    if (card.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Favorita",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp)
                    .padding(end = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!buttonEnabled) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        onClick = onAdd
                    ) {
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}