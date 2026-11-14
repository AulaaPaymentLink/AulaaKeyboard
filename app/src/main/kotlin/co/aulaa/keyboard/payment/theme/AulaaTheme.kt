/*
 * Copyright (C) 2026 Aulaa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package co.aulaa.keyboard.payment.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Theme entry point for every Aulaa-owned surface (the payment panel in the IME,
 * and the companion-app pairing screen). It exposes the dashboard-derived
 * [AulaaPalette] through [LocalAulaaPalette] and also wires a Material3 scheme so
 * stock Material components inherit the same colors.
 *
 * Radius follows the dashboard's `--radius: 0.625rem` ≈ 10dp.
 */
val LocalAulaaPalette = staticCompositionLocalOf { AulaaLightPalette }

object AulaaTheme {
    val palette: AulaaPalette
        @Composable get() = LocalAulaaPalette.current

    val radius = 10.dp
    val radiusSm = 7.dp
    val radiusLg = 14.dp
}

@Composable
fun AulaaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val p = if (darkTheme) AulaaDarkPalette else AulaaLightPalette

    // Material primary = Aulaa brand blue, so Material primary actions read as
    // Aulaa actions. Surfaces/text come from the neutral dashboard tokens.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = p.brandDeep,
            onPrimary = p.onBrand,
            background = p.background,
            onBackground = p.foreground,
            surface = p.card,
            onSurface = p.cardForeground,
            surfaceVariant = p.muted,
            onSurfaceVariant = p.mutedForeground,
            secondary = p.secondary,
            outline = p.ring,
            outlineVariant = p.border,
        )
    } else {
        lightColorScheme(
            primary = p.brandDeep,
            onPrimary = p.onBrand,
            background = p.background,
            onBackground = p.foreground,
            surface = p.card,
            onSurface = p.cardForeground,
            surfaceVariant = p.muted,
            onSurfaceVariant = p.mutedForeground,
            secondary = p.secondary,
            outline = p.ring,
            outlineVariant = p.border,
        )
    }

    val shapes = Shapes(
        small = RoundedCornerShape(AulaaTheme.radiusSm),
        medium = RoundedCornerShape(AulaaTheme.radius),
        large = RoundedCornerShape(AulaaTheme.radiusLg),
    )

    CompositionLocalProvider(LocalAulaaPalette provides p) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
    }
}
