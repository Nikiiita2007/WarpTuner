package com.example.warptuner.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private const val THEME_FADE_MS = 350

@Composable
private fun Color.fade(): Color =
    animateColorAsState(this, tween(THEME_FADE_MS), label = "themeColor").value

/** Возвращает схему, цвета которой плавно перетекают при смене темы. */
@Composable
fun ColorScheme.animated(): ColorScheme = copy(
    primary = primary.fade(),
    onPrimary = onPrimary.fade(),
    primaryContainer = primaryContainer.fade(),
    onPrimaryContainer = onPrimaryContainer.fade(),
    inversePrimary = inversePrimary.fade(),
    secondary = secondary.fade(),
    onSecondary = onSecondary.fade(),
    secondaryContainer = secondaryContainer.fade(),
    onSecondaryContainer = onSecondaryContainer.fade(),
    tertiary = tertiary.fade(),
    onTertiary = onTertiary.fade(),
    tertiaryContainer = tertiaryContainer.fade(),
    onTertiaryContainer = onTertiaryContainer.fade(),
    background = background.fade(),
    onBackground = onBackground.fade(),
    surface = surface.fade(),
    onSurface = onSurface.fade(),
    surfaceVariant = surfaceVariant.fade(),
    onSurfaceVariant = onSurfaceVariant.fade(),
    surfaceTint = surfaceTint.fade(),
    inverseSurface = inverseSurface.fade(),
    inverseOnSurface = inverseOnSurface.fade(),
    error = error.fade(),
    onError = onError.fade(),
    errorContainer = errorContainer.fade(),
    onErrorContainer = onErrorContainer.fade(),
    outline = outline.fade(),
    outlineVariant = outlineVariant.fade(),
    scrim = scrim.fade(),
    surfaceBright = surfaceBright.fade(),
    surfaceDim = surfaceDim.fade(),
    surfaceContainer = surfaceContainer.fade(),
    surfaceContainerHigh = surfaceContainerHigh.fade(),
    surfaceContainerHighest = surfaceContainerHighest.fade(),
    surfaceContainerLow = surfaceContainerLow.fade(),
    surfaceContainerLowest = surfaceContainerLowest.fade()
)
