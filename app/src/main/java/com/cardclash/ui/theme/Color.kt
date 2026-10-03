package com.cardclash.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Paleta de marca de CardClash: violeta (identidad) + dorado (rareza/coleccion).
// ---------------------------------------------------------------------------

// Light scheme
val PurplePrimaryLight = Color(0xFF5B3DF5)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE8DEFF)
val OnPrimaryContainerLight = Color(0xFF1F0B63)

val GoldSecondaryLight = Color(0xFF8A6D00)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFFFDF9E)
val OnSecondaryContainerLight = Color(0xFF2A1F00)

val BackgroundLight = Color(0xFFFCF8FF)
val OnBackgroundLight = Color(0xFF1B1B21)
val SurfaceLight = Color(0xFFFCF8FF)
val OnSurfaceLight = Color(0xFF1B1B21)
val SurfaceVariantLight = Color(0xFFE7E0EE)
val OnSurfaceVariantLight = Color(0xFF494450)
val OutlineLight = Color(0xFF7A7383)

val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)

// Dark scheme
val PurplePrimaryDark = Color(0xFFCBC2FF)
val OnPrimaryDark = Color(0xFF2D1387)
val PrimaryContainerDark = Color(0xFF4A30CB)
val OnPrimaryContainerDark = Color(0xFFE8DEFF)

val GoldSecondaryDark = Color(0xFFF0C14D)
val OnSecondaryDark = Color(0xFF423400)
val SecondaryContainerDark = Color(0xFF5F4C00)
val OnSecondaryContainerDark = Color(0xFFFFDF9E)

val BackgroundDark = Color(0xFF141218)
val OnBackgroundDark = Color(0xFFE6E1E9)
val SurfaceDark = Color(0xFF141218)
val OnSurfaceDark = Color(0xFFE6E1E9)
val SurfaceVariantDark = Color(0xFF494450)
val OnSurfaceVariantDark = Color(0xFFCAC4D0)
val OutlineDark = Color(0xFF948E9E)

val ErrorDark = Color(0xFFF1B8B5)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)

// ---------------------------------------------------------------------------
// Colores semanticos de UI (funcionan sobre surface clara y oscura).
// ---------------------------------------------------------------------------

/** Color de badge de rareza común (acero azulado). */
val RarityCommonColor = Color(0xFF5E6B7A)

/** Color de badge de rareza SR (violeta). */
val RaritySrColor = Color(0xFF7C4DFF)

/** Color de badge de rareza SSR (dorado oscuro, contraste sobre blanco). */
val RaritySsrColor = Color(0xFFB8860B)

/** Texto de los badges de rareza (blanco sobre color de badge). */
val RarityOnBadgeColor = Color(0xFFFFFFFF)

/** Resultado victoria en historial. */
val VictoryColor = Color(0xFF2E7D32)

/** Resultado derrota en historial. */
val DefeatColor = Color(0xFFC62828)