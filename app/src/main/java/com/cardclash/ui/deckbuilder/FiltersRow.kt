package com.cardclash.ui.deckbuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.spacedBy
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardclash.domain.model.Rarity
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.FilterState
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CardTypeFilter
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.CopyFilter
import com.cardclash.ui.theme.CardClashTheme
import com.cardclash.ui.theme.RarityCommonColor
import com.cardclash.ui.theme.RaritySsrColor
import com.cardclash.ui.theme.RaritySrColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.spacedBy
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selectable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag

@Composable
fun FiltersRow(
    filter: FilterState,
    onTypeFilterChange: (CardTypeFilter) -> Unit,
    onRarityFilterChange: (Rarity) -> Unit,
    onManaCostFilterChange: (Int) -> Unit,
    onCopyFilterChange: (CopyFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onOnlyOwnedChange: (Boolean) -> Unit,
    onOnlyCompatibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchText by remember { mutableStateOf("") }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("filters_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(filterSections(
            filter = filter,
            onTypeFilterChange = onTypeFilterChange,
            onRarityFilterChange = onRarityFilterChange,
            onManaCostFilterChange = onManaCostFilterChange,
            onCopyFilterChange = onCopyFilterChange,
            onOnlyOwnedChange = onOnlyOwnedChange,
            onOnlyCompatibleChange = onOnlyCompatibleChange,
            searchText = searchText,
            onSearchChange = { searchText = it; onSearchChange(it) },
        )) { section ->
            section()
        }
    }
}

@Composable
private fun FilterSectionDivider() {
    Divider(
        modifier = Modifier
            .height(32.dp)
            .width(1.dp)
            .padding(top = 12.dp, bottom = 12.dp),
        color = MaterialTheme.colorScheme.outline,
        thickness = 1.dp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun filterSections(
    filter: FilterState,
    onTypeFilterChange: (CardTypeFilter) -> Unit,
    onRarityFilterChange: (Rarity) -> Unit,
    onManaCostFilterChange: (Int) -> Unit,
    onCopyFilterChange: (CopyFilter) -> Unit,
    onOnlyOwnedChange: (Boolean) -> Unit,
    onOnlyCompatibleChange: (Boolean) -> Unit,
    searchText: String,
    onSearchChange: (String) -> Unit,
): List<@Composable () -> Unit> {
    return listOf(
        { SearchField(searchText = searchText, onSearchChange = onSearchChange) },
        { FilterSectionDivider() },
        { TypeFiltersSection(filter = filter, onTypeFilterChange = onTypeFilterChange) },
        { FilterSectionDivider() },
        { RarityFiltersSection(filter = filter, onRarityFilterChange = onRarityFilterChange) },
        { FilterSectionDivider() },
        { ManaCostFiltersSection(filter = filter, onManaCostFilterChange = onManaCostFilterChange) },
        { FilterSectionDivider() },
        { CopyFiltersSection(filter = filter, onCopyFilterChange = onCopyFilterChange) },
        { FilterSectionDivider() },
        { ToggleFilterChip(
            label = "Solo propias",
            selected = filter.onlyOwned,
            onClick = onOnlyOwnedChange,
            role = Role.Checkbox,
            contentDescription = "Filtrar solo cartas que posees",
        ) },
        { ToggleFilterChip(
            label = "Solo compatibles",
            selected = filter.onlyCompatible,
            onClick = onOnlyCompatibleChange,
            role = Role.Checkbox,
            contentDescription = "Filtrar solo cartas compatibles con el héroe",
        ) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    searchText: String,
    onSearchChange: (String) -> Unit,
) {
    val textFieldColors = TextFieldDefaults.textFieldColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    )

    TextField(
        value = searchText,
        onValueChange = onSearchChange,
        modifier = Modifier.width(200.dp).height(48.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        placeholder = { Text("Buscar cartas...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        singleLine = true,
        colors = textFieldColors,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeFiltersSection(
    filter: FilterState,
    onTypeFilterChange: (CardTypeFilter) -> Unit,
) {
    val types = listOf(
        CardTypeFilter.ALL to "Todas",
        CardTypeFilter.ATTACK to "Ataque",
        CardTypeFilter.HEAL to "Cura",
        CardTypeFilter.DRAW to "Robo",
        CardTypeFilter.STATUS to "Estado",
        CardTypeFilter.PASSIVE to "Pasiva",
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(types) { (type, label) ->
            val isSelected = if (type == CardTypeFilter.ALL) {
                filter.types.isEmpty() || filter.types.contains(CardTypeFilter.ALL)
            } else {
                filter.types.contains(type)
            }

            MultiSelectFilterChip(
                label = label,
                selected = isSelected,
                onClick = { onTypeFilterChange(type) },
                contentDescription = "Filtro por tipo: $label",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RarityFiltersSection(
    filter: FilterState,
    onRarityFilterChange: (Rarity) -> Unit,
) {
    val rarities = listOf(
        Rarity.COMMON to "Común",
        Rarity.SR to "Super Rara",
        Rarity.SSR to "Ultra Rara",
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rarities) { (rarity, label) ->
            val isSelected = filter.rarities.contains(rarity)
            val rarityColor = when (rarity) {
                Rarity.COMMON -> RarityCommonColor
                Rarity.SR -> RaritySrColor
                Rarity.SSR -> RaritySsrColor
            }

            MultiSelectFilterChip(
                label = label,
                selected = isSelected,
                onClick = { onRarityFilterChange(rarity) },
                contentDescription = "Filtro por rareza: $label",
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(12.dp)
                            .background(rarityColor, RoundedCornerShape(4.dp)),
                    )
                },
                selectedContainerColor = rarityColor.copy(alpha = 0.2f),
                selectedContentColor = rarityColor,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManaCostFiltersSection(
    filter: FilterState,
    onManaCostFilterChange: (Int) -> Unit,
) {
    val costs = listOf(0, 1, 2, 3, 4, 5, 6, 7)

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(costs) { cost ->
            val isSelected = filter.manaCosts.contains(cost)
            val label = if (cost >= 7) "7+" else cost.toString()

            SingleSelectFilterChip(
                label = label,
                selected = isSelected,
                onClick = { onManaCostFilterChange(cost) },
                contentDescription = "Filtro por coste de maná: $label",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyFiltersSection(
    filter: FilterState,
    onCopyFilterChange: (CopyFilter) -> Unit,
) {
    val copyFilters = listOf(
        CopyFilter.All to "Todas",
        CopyFilter.Owned to "Disponibles",
        CopyFilter.Missing to "Sin copias",
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(copyFilters) { (copyFilter, label) ->
            val isSelected = filter.copyFilter == copyFilter

            SingleSelectFilterChip(
                label = label,
                selected = isSelected,
                onClick = { onCopyFilterChange(copyFilter) },
                contentDescription = "Filtro por copias: $label",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .padding(horizontal = 4.dp)
            .semantics {
                role = Role.Checkbox
                this.contentDescription = contentDescription
                selectable = selected
            },
        label = {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.invoke()
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = selectedContentColor,
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = selectedContainerColor,
            selectedLabelColor = selectedContentColor,
            selectedIconColor = selectedContentColor,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleSelectFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .padding(horizontal = 4.dp)
            .semantics {
                role = Role.RadioButton
                this.contentDescription = contentDescription
                selectable = selected
            },
        label = {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToggleFilterChip(
    label: String,
    selected: Boolean,
    onClick: (Boolean) -> Unit,
    role: Role,
    contentDescription: String,
) {
    FilterChip(
        selected = selected,
        onClick = { onClick(!selected) },
        modifier = Modifier
            .height(48.dp)
            .padding(horizontal = 4.dp)
            .semantics {
                this.role = role
                this.contentDescription = contentDescription
                selectable = selected
            },
        label = {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}