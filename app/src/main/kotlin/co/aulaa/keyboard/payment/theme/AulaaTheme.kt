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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
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

/**
 * Official Aulaa double-chevron brand mark (`public/aulaa-icon.svg` from `apps/tenant-dashboard`).
 *
 * Top half of each chevron uses linear gradient `#7AD7F4` -> `#43BAE8`;
 * bottom half uses linear gradient `#319FD6` -> `#2183B9`.
 */
@Composable
fun AulaaBrandMark(
    size: Dp,
    modifier: Modifier = Modifier,
    showContainer: Boolean = true,
) {
    val p = AulaaTheme.palette
    val cornerRadius = (size.value * 0.26f).dp
    val boxModifier = if (showContainer) {
        modifier
            .size(size)
            .background(p.card, RoundedCornerShape(cornerRadius))
            .border(BorderStroke(1.dp, p.border), RoundedCornerShape(cornerRadius))
            .padding(size * 0.14f)
    } else {
        modifier.size(size)
    }

    Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            // Coordinate space from aulaa-icon.svg: viewBox 0..128 x 0..128
            fun sx(x: Float) = (x / 128f) * w
            fun sy(y: Float) = (y / 128f) * h

            val topBrushLeft = Brush.linearGradient(
                colors = listOf(Color(0xFF7AD7F4), Color(0xFF43BAE8)),
                start = Offset(sx(12f), sy(24f)),
                end = Offset(sx(76.8f), sy(64.5f)),
            )
            val botBrushLeft = Brush.linearGradient(
                colors = listOf(Color(0xFF319FD6), Color(0xFF2183B9)),
                start = Offset(sx(12f), sy(64.5f)),
                end = Offset(sx(76.8f), sy(105f)),
            )
            val topBrushRight = Brush.linearGradient(
                colors = listOf(Color(0xFF7AD7F4), Color(0xFF43BAE8)),
                start = Offset(sx(51.15f), sy(24f)),
                end = Offset(sx(115.95f), sy(64.5f)),
            )
            val botBrushRight = Brush.linearGradient(
                colors = listOf(Color(0xFF319FD6), Color(0xFF2183B9)),
                start = Offset(sx(51.15f), sy(64.5f)),
                end = Offset(sx(115.95f), sy(105f)),
            )

            // Left chevron top
            drawPath(
                path = Path().apply {
                    moveTo(sx(12f), sy(24f))
                    lineTo(sx(39f), sy(24f))
                    lineTo(sx(76.8f), sy(64.5f))
                    lineTo(sx(49.8f), sy(64.5f))
                    close()
                },
                brush = topBrushLeft,
            )
            // Left chevron bottom
            drawPath(
                path = Path().apply {
                    moveTo(sx(49.8f), sy(64.5f))
                    lineTo(sx(76.8f), sy(64.5f))
                    lineTo(sx(39f), sy(105f))
                    lineTo(sx(12f), sy(105f))
                    close()
                },
                brush = botBrushLeft,
            )
            // Right chevron top
            drawPath(
                path = Path().apply {
                    moveTo(sx(51.15f), sy(24f))
                    lineTo(sx(78.15f), sy(24f))
                    lineTo(sx(115.95f), sy(64.5f))
                    lineTo(sx(88.95f), sy(64.5f))
                    close()
                },
                brush = topBrushRight,
            )
            // Right chevron bottom
            drawPath(
                path = Path().apply {
                    moveTo(sx(88.95f), sy(64.5f))
                    lineTo(sx(115.95f), sy(64.5f))
                    lineTo(sx(78.15f), sy(105f))
                    lineTo(sx(51.15f), sy(105f))
                    close()
                },
                brush = botBrushRight,
            )
        }
    }
}
