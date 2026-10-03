package com.cardclash.ui.deckbuilder

import android.graphics.PathEffect as AndroidPathEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.spacedBy
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BorderStroke
import androidx.compose.material3.BorderStrokeType
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toPx
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.unit.size
import com.cardclash.domain.model.CardId
import com.cardclash.domain.model.Rarity
import com.cardclash.ui.common.RarityBadge
import com.cardclash.ui.theme.RarityCommonColor
import com.cardclash.ui.theme.RaritySsrColor
import com.cardclash.ui.theme.RaritySrColor
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.SlotValidationStatus
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.ValidationError
import com.cardclash.ui.deckbuilder.DeckBuilderUiState.DeckSlotUi

private data class BorderConfig(
    val containerColor: Color,
    val borderColor: Color,
    val borderWidth: androidx.compose.ui.unit.Dp,
    val borderStyle: BorderStrokeType
)

@Composable
fun DeckSlot(
    slot: DeckSlotUi,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onDetail: () -> Unit,
    slotErrors: List<ValidationError>,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val slotSize = if (isCompact) 72.dp else 96.dp
    val state = slot.state
    val isPassive = slot.isPassive
    val hasCard = slot.cardId != null

    val borderConfig = when (state) {
        SlotValidationStatus.Empty -> {
            val outlineColor = MaterialTheme.colorScheme.outline
            BorderConfig(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                borderColor = outlineColor,
                borderWidth = 2.dp,
                borderStyle = BorderStrokeType.Dashed
            )
        }
        SlotValidationStatus.Filled -> BorderConfig(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            borderColor = MaterialTheme.colorScheme.primary,
            borderWidth = 1.dp,
            borderStyle = BorderStrokeType.Solid
        )
        SlotValidationStatus.Error -> BorderConfig(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
            borderColor = MaterialTheme.colorScheme.error,
            borderWidth = 2.dp,
            borderStyle = BorderStrokeType.Solid
        )
        SlotValidationStatus.Warning -> BorderConfig(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            borderColor = Color(0xFFF59E0B),
            borderWidth = 1.dp,
            borderStyle = BorderStrokeType.Solid
        )
    }

    val containerColor = borderConfig.containerColor
    val borderColor = borderConfig.borderColor
    val borderWidth = borderConfig.borderWidth
    val borderStyle = borderConfig.borderStyle

    val contentDescription = when (state) {
        SlotValidationStatus.Empty -> if (isPassive) "Slot pasiva vacía, tocar para añadir carta pasiva" else "Slot vacía, tocar para añadir carta"
        SlotValidationStatus.Filled -> "Carta ${slot.name ?: "desconocida"}, coste ${slot.manaCost ?: 0}, rareza ${slot.rarity?.name ?: "desconocida"}"
        SlotValidationStatus.Error -> "Carta con error: ${slotErrors.joinToString(", ") { it.message }}"
        SlotValidationStatus.Warning -> "Carta con advertencia: ${slotErrors.joinToString(", ") { it.message }}"
    }

    val stateDescription = when (state) {
        SlotValidationStatus.Empty -> if (isPassive) "Vacía (pasiva)" else "Vacía"
        SlotValidationStatus.Filled -> "Ocupada"
        SlotValidationStatus.Error -> "Error"
        SlotValidationStatus.Warning -> "Advertencia"
    }

    Card(
        modifier = modifier
            .width(slotSize)
            .height(slotSize)
            .semantics {
                role = Role.Button
                stateDescription = stateDescription
                contentDescription = contentDescription
                onClick(label = "Seleccionar slot") { onClick(); true }
            }
            .clip(RoundedCornerShape(12.dp))
            .focusable(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = borderWidth,
            color = borderColor,
            pathEffect = when (borderStyle) {
                BorderStrokeType.Dashed -> AndroidPathEffect.makeDashPathEffect(floatArrayOf(8f, 4f), 0f)
                BorderStrokeType.Solid -> null
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                SlotValidationStatus.Empty -> EmptySlotContent(
                    isPassive = isPassive,
                    isCompact = isCompact
                )
                SlotValidationStatus.Filled -> FilledSlotContent(
                    slot = slot,
                    onRemove = onRemove,
                    onDetail = onDetail,
                    isCompact = isCompact
                )
                SlotValidationStatus.Error -> ErrorSlotContent(
                    slot = slot,
                    slotErrors = slotErrors,
                    onRemove = onRemove,
                    onDetail = onDetail,
                    isCompact = isCompact
                )
                SlotValidationStatus.Warning -> WarningSlotContent(
                    slot = slot,
                    slotErrors = slotErrors,
                    onRemove = onRemove,
                    onDetail = onDetail,
                    isCompact = isCompact
                )
            }
        }
    }
}

@Composable
private fun EmptySlotContent(isPassive: Boolean, isCompact: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        arrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
    ) {
        if (isPassive) {
            Surface(
                modifier = Modifier.padding(bottom = if (isCompact) 2.dp else 4.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text(
                    text = "PASIVA",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Icon(
            imageVector = if (isPassive) Icons.Default.Shield else Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(if (isCompact) 24.dp else 32.dp)
        )

        Text(
            text = if (isPassive) "Pasiva requerida" else "Requerida",
            style = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = {},
            modifier = Modifier
                .size(if (isCompact) 36.dp else 48.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = if (isPassive) "Añadir carta pasiva" else "Añadir carta"
                },
            colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
            )
        }
    }
}

@Composable
private fun FilledSlotContent(
    slot: DeckSlotUi,
    onRemove: () -> Unit,
    onDetail: () -> Unit,
    isCompact: Boolean
) {
    val rarityColor = when (slot.rarity) {
        Rarity.COMMON -> RarityCommonColor
        Rarity.SR -> RaritySrColor
        Rarity.SSR -> RaritySsrColor
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 8.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(if (isCompact) 16.dp else 20.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(if (isCompact) 12.dp else 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = slot.name ?: "",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Mana: ${slot.manaCost ?: 0}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                textAlign = TextAlign.Center
            )

            if (slot.rarity != null) {
                RarityBadge(
                    label = slot.rarity!!.name,
                    badgeColor = rarityColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 4.dp else 8.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
            ) {
                IconButton(
                    onClick = onDetail,
                    modifier = Modifier
                        .size(if (isCompact) 32.dp else 40.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Ver detalle de ${slot.name ?: "carta"}"
                        },
                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(if (isCompact) 32.dp else 40.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Quitar ${slot.name ?: "carta"}"
                        },
                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorSlotContent(
    slot: DeckSlotUi,
    slotErrors: List<ValidationError>,
    onRemove: () -> Unit,
    onDetail: () -> Unit,
    isCompact: Boolean
) {
    val hasOwnershipError = slotErrors.any { it.code == "NOT_OWNED" || it.message.contains("posees") }
    val errorMessage = slotErrors.joinToString("; ") { it.message }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 8.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = if (isCompact) 2.dp else 4.dp, end = if (isCompact) 2.dp else 4.dp)
                ) {
                    Text(
                        text = "⚠ $errorMessage",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (slot.name != null) {
                Text(
                    text = slot.name!!,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Mana: ${slot.manaCost ?: 0}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center
                )

                if (slot.rarity != null) {
                    val rarityColor = when (slot.rarity) {
                        Rarity.COMMON -> RarityCommonColor
                        Rarity.SR -> RaritySrColor
                        Rarity.SSR -> RaritySsrColor
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    RarityBadge(
                        label = slot.rarity!!.name,
                        badgeColor = rarityColor,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        if (hasOwnershipError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(if (isCompact) 24.dp else 32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No posees",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 4.dp else 8.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
            ) {
                IconButton(
                    onClick = onDetail,
                    modifier = Modifier
                        .size(if (isCompact) 32.dp else 40.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Ver detalle de ${slot.name ?: "carta"}"
                        },
                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(if (isCompact) 32.dp else 40.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Quitar ${slot.name ?: "carta"}"
                        },
                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningSlotContent(
    slot: DeckSlotUi,
    slotErrors: List<ValidationError>,
    onRemove: () -> Unit,
    onDetail: () -> Unit,
    isCompact: Boolean
) {
    val warningMessage = slotErrors.joinToString("; ") { it.message }
    val warningColor = Color(0xFFF59E0B)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 8.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = warningColor.copy(alpha = 0.15f),
                    contentColor = warningColor,
                    modifier = Modifier.padding(top = if (isCompact) 2.dp else 4.dp, end = if (isCompact) 2.dp else 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = warningColor,
                            modifier = Modifier.size(if (isCompact) 12.dp else 14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = warningMessage,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = warningColor
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (slot.name != null) {
                Text(
                    text = slot.name!!,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Mana: ${slot.manaCost ?: 0}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center
                )

                if (slot.rarity != null) {
                    val rarityColor = when (slot.rarity) {
                        Rarity.COMMON -> RarityCommonColor
                        Rarity.SR -> RaritySrColor
                        Rarity.SSR -> RaritySsrColor
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    RarityBadge(
                        label = slot.rarity!!.name,
                        badgeColor = rarityColor,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 4.dp else 8.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
            ) {
                IconButton(
                    onClick = onDetail,
                    modifier = Modifier
                        .size(if (isCompact) 32.dp else 40.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Ver detalle de ${slot.name ?: "carta"}"
                        },
                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(if (isCompact) 32.dp else 40.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Quitar ${slot.name ?: "carta"}"
                        },
                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
                    )
                }
            }
        }
    }
}