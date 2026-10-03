package com.cardclash.ui.deckbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.spacedBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.compose.popBackStack
import androidx.window.layout.calculateWindowSizeClass
import androidx.window.layout.WindowWidthSizeClass
import com.cardclash.di.AppContainer
import com.cardclash.domain.progression.ProgressionRules
import com.cardclash.ui.theme.CardClashTheme
import kotlinx.coroutines.launch
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.DeckUiModel
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.FilterState
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ValidationResult
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ManaCurveData
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ValidationError
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.DeckSlotUi
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CollectionCardUi
import com.cardclash.ui.deckbuilder.SlotsRow
import com.cardclash.ui.deckbuilder.ManaCurveChart
import com.cardclash.ui.deckbuilder.CollectionBottomSheet
import com.cardclash.ui.deckbuilder.FiltersRow
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CardTypeFilter
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CopyFilter
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.BottomSheetValue
import androidx.compose.material3.BottomSheetScaffoldDefaults
import androidx.compose.material3.BottomSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckBuilderScreen(
    container: AppContainer,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val vm: DeckBuilderViewModel = viewModel {
        DeckBuilderViewModel(
            deckRepository = container.repository,
            catalog = container.catalog,
            progressRepository = container.repository,
            savedStateHandle = SavedStateHandle(),
            rules = ProgressionRules()
        )
    }
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    when (uiState) {
        is DeckBuilderUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is DeckBuilderUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is DeckBuilderUiState.Saved -> {
            LaunchedEffect(Unit) {
                navController.popBackStack()
            }
            Box(modifier = modifier.fillMaxSize())
        }
        is DeckBuilderUiState.ConfirmDiscardChanges -> {
            EditingContent(
                state = DeckBuilderUiState.Editing(
                    deck = DeckBuilderUiState.DeckUiModel(
                        id = null,
                        name = "",
                        slots = emptyList(),
                        nonPassiveCount = 0,
                        passiveCount = 0,
                        totalCopiesInDeck = 0
                    ),
                    collection = emptyList(),
                    filter = DeckBuilderUiState.FilterState(),
                    validation = DeckBuilderUiState.ValidationResult.empty(),
                    manaCurve = DeckBuilderUiState.ManaCurveData(
                        totalCards = 0,
                        byCost = emptyMap(),
                        byCostAndRarity = emptyMap(),
                        passiveCount = 0
                    )
                ),
                onBackPress = { uiState.onCancel() },
                onNameChange = {},
                onSlotClick = {},
                onSlotCardRemove = {},
                onSlotCardDetail = {},
                onSaveClick = {},
                onDiscardChanges = {},
                onCollectionExpandChanged = {},
                onFilterChange = {},
                onCollectionCardAdd = {},
                modifier = modifier
            )
        }
        is DeckBuilderUiState.Editing -> {
            EditingContent(
                state = uiState,
                onBackPress = { vm.onBackPress() },
                onNameChange = vm::onNameChange,
                onSlotClick = vm::onSlotClick,
                onSlotCardRemove = vm::onSlotCardRemove,
                onSlotCardDetail = vm::onSlotCardDetail,
                onSaveClick = vm::onSaveClick,
                onDiscardChanges = vm::onDiscardChanges,
                onCollectionExpandChanged = vm::onCollectionExpandChanged,
                onFilterChange = vm::onFilterChange,
                onCollectionCardAdd = vm::onCollectionCardAdd,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun EditingContent(
    state: DeckBuilderUiState.Editing,
    onBackPress: () -> Unit,
    onNameChange: (String) -> Unit,
    onSlotClick: (Int) -> Unit,
    onSlotCardRemove: (Int) -> Unit,
    onSlotCardDetail: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onDiscardChanges: () -> Unit,
    onCollectionExpandChanged: (Boolean) -> Unit,
    onFilterChange: (DeckBuilderUiState.FilterState) -> Unit,
    onCollectionCardAdd: (DeckBuilderUiState.CollectionCardUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context).widthSizeClass
    val isCompact = windowSizeClass == WindowWidthSizeClass.COMPACT

    val bottomSheetState = remember {
        BottomSheetState(
            initialValue = if (state.isCollectionExpanded)
                BottomSheetValue.Expanded
            else
                BottomSheetValue.Collapsed,
            confirmStateChange = { value ->
                when (value) {
                    BottomSheetValue.Collapsed,
                    BottomSheetValue.HalfExpanded,
                    BottomSheetValue.Expanded -> true
                    else -> false
                }
            },
            skipHalfExpanded = false,
            skipCollapsed = false
        )
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.isCollectionExpanded) {
        val targetValue = if (state.isCollectionExpanded)
            BottomSheetValue.Expanded
        else
            BottomSheetValue.Collapsed
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

    val peekHeight = derivedStateOf {
        when {
            isSheetExpanded.value -> BottomSheetScaffoldDefaults.ExpandedSheetHeight
            isSheetHalfExpanded.value -> 300.dp
            else -> 120.dp
        }
    }

    BottomSheetScaffold(
        modifier = modifier.fillMaxSize(),
        sheetState = bottomSheetState,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetPeekHeight = peekHeight.value,
        sheetContent = {
            CollectionBottomSheet(
                isExpanded = isSheetExpanded.value,
                onExpandChanged = { expanded ->
                    scope.launch {
                        bottomSheetState.animateTo(
                            if (expanded) BottomSheetValue.Expanded
                            else BottomSheetValue.Collapsed
                        )
                    }
                    onCollectionExpandChanged(expanded)
                },
                filter = state.filter,
                onFilterChange = onFilterChange,
                collection = state.collection,
                onCardAdd = onCollectionCardAdd,
                onCardDetail = { /* handled by CollectionBottomSheet internally */ },
                modifier = Modifier
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
                        onCollectionExpandChanged(
                            nextValue == BottomSheetValue.Expanded ||
                            nextValue == BottomSheetValue.HalfExpanded
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = 16.dp)
        ) {
            TopAppBarContent(
                deckName = state.deck.name,
                onBackPress = onBackPress,
                onNameChange = onNameChange,
                onSaveClick = onSaveClick,
                onDiscardChanges = onDiscardChanges,
                isSaving = state.isSaving,
                isValid = state.validation.isValid,
                isModified = state.deck.isModified
            )

            ValidationStatusBar(
                validation = state.validation,
                deck = state.deck,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, top = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            SlotsRow(
                slots = state.deck.slots,
                onSlotClick = onSlotClick,
                onSlotRemove = onSlotCardRemove,
                onSlotDetail = onSlotCardDetail,
                validation = state.validation,
                isCompact = isCompact,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            ManaCurveChart(
                data = state.manaCurve,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TopAppBarContent(
    deckName: String,
    onBackPress: () -> Unit,
    onNameChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onDiscardChanges: () -> Unit,
    isSaving: Boolean,
    isValid: Boolean,
    isModified: Boolean
) {
    TopAppBar(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.AppBar },
        title = {
            TextField(
                value = deckName,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        role = Role.TextField
                        contentDescription = "Nombre del mazo: $deckName"
                    },
                maxLines = 1,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                colors = androidx.compose.material3.TextFieldDefaults.textFieldColors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { /* handled by IME */ }
                )
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBackPress,
                modifier = Modifier
                    .semantics {
                        role = Role.Button
                        contentDescription = "Volver"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            Row(
                modifier = Modifier.padding(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isModified) {
                    Button(
                        onClick = onDiscardChanges,
                        modifier = Modifier
                            .semantics {
                                role = Role.Button
                                contentDescription = "Descartar cambios"
                            }
                    ) {
                        Text("Descartar", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Button(
                    onClick = onSaveClick,
                    enabled = isValid && !isSaving,
                    modifier = Modifier
                        .semantics {
                            role = Role.Button
                            contentDescription = if (isSaving) "Guardando..." else "Guardar mazo"
                        }
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Text("Guardar", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun ValidationStatusBar(
    validation: DeckBuilderUiState.ValidationResult,
    deck: DeckBuilderUiState.DeckUiModel,
    modifier: Modifier = Modifier
) {
    val totalCards = deck.totalCopiesInDeck
    val requiredNonPassive = 8
    val requiredPassive = 1

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Status
                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
            },
        colors = CardDefaults.cardColors(
            containerColor = if (validation.isValid)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (validation.isValid) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (validation.isValid)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (validation.isValid) "VÁLIDO" : "ERRORES (${validation.errors.size})",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (validation.isValid)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }

                MiniManaCurve(
                    data = deck.slots,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Text(
                text = "$totalCards/${requiredNonPassive + requiredPassive} cartas (${deck.nonPassiveCount} normales, ${deck.passiveCount} pasivas)",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (validation.isValid)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            )

            if (!validation.isValid && validation.errors.isNotEmpty()) {
                ExpandableErrorsList(errors = validation.errors)
            }
        }
    }
}

@Composable
private fun MiniManaCurve(
    data: List<DeckBuilderUiState.DeckSlotUi>,
    modifier: Modifier = Modifier
) {
    val costCounts = data
        .filter { it.cardId != null && !it.isPassive }
        .groupBy { it.manaCost ?: 0 }
        .mapValues { it.value.size }
    val maxCost = costCounts.keys.maxOrNull() ?: 5
    val maxCount = costCounts.values.maxOrNull() ?: 1

    Row(
        modifier = modifier
            .width(120.dp)
            .height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (cost in 0..maxCost.coerceAtMost(7)) {
            val count = costCounts[cost] ?: 0
            val barHeight = (count.toFloat() / maxCount * 20.dp).coerceAtLeast(2.dp)
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(barHeight)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun ExpandableErrorsList(errors: List<DeckBuilderUiState.ValidationError>) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ver ${errors.size} error${if (errors.size != 1) "es" else ""}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        if (expanded) {
            Divider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(errors) { error ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = error.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                            if (error.slotIndex != null) {
                                Text(
                                    text = "Slot ${error.slotIndex!! + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    }
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
                androidx.compose.ui.semantics.stateDescription = stateDesc
                contentDescription = stateDesc
            }
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp, 4.dp)
                .graphicsLayer { this.rotationZ = rotation }
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DeckBuilderScreenPreview() {
    CardClashTheme {
        DeckBuilderScreen(
            container = AppContainer(LocalContext.current),
            navController = androidx.navigation.compose.rememberNavController()
        )
    }
}