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

import androidx.compose.ui.graphics.Color

/**
 * Aulaa Keyboard palette.
 *
 * The neutral surface/text tokens are lifted verbatim from the Aulaa
 * tenant-dashboard design system (shadcn "neutral" base, originally expressed as
 * oklch in `apps/tenant-dashboard/src/app/globals.css`). The sRGB hex values here
 * are the dashboard's own neutral scale; the source oklch token is noted beside
 * each so they can be re-derived exactly when fine-tuning.
 *
 * The Aulaa brand blue is reserved for the brand mark and primary actions only —
 * never for surfaces or body text — matching how the dashboard uses it.
 */
data class AulaaPalette(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val secondary: Color,
    val border: Color,
    val input: Color,
    val ring: Color,
    val primary: Color,
    val primaryForeground: Color,
    // Aulaa brand blue (#2183B9 -> #43BAE8), actions + brand mark only.
    val brandDeep: Color,   // #2183B9
    val brandMid: Color,    // #319FD6
    val brandLight: Color,  // #43BAE8
    val onBrand: Color,
    val brandTint: Color,   // low-chroma fill behind brand text/chips
    // Keyboard key surfaces.
    val keyboardBackground: Color,
    val key: Color,
    val keyForeground: Color,
    val keyAlt: Color,
)

// Brand blue is theme-independent.
private val BrandDeep = Color(0xFF2183B9)
private val BrandMid = Color(0xFF319FD6)
private val BrandLight = Color(0xFF43BAE8)

val AulaaLightPalette = AulaaPalette(
    background = Color(0xFFFFFFFF),          // oklch(1 0 0)
    foreground = Color(0xFF0A0A0A),          // oklch(0.145 0 0)
    card = Color(0xFFFFFFFF),
    cardForeground = Color(0xFF0A0A0A),
    muted = Color(0xFFF5F5F5),               // oklch(0.97 0 0)
    mutedForeground = Color(0xFF737373),     // oklch(0.556 0 0)
    secondary = Color(0xFFF5F5F5),
    border = Color(0xFFE5E5E5),              // oklch(0.922 0 0)
    input = Color(0xFFE5E5E5),
    ring = Color(0xFFA3A3A3),                // oklch(0.708 0 0)
    primary = Color(0xFF171717),             // oklch(0.205 0 0)
    primaryForeground = Color(0xFFFAFAFA),   // oklch(0.985 0 0)
    brandDeep = BrandDeep,
    brandMid = BrandMid,
    brandLight = BrandLight,
    onBrand = Color(0xFFFFFFFF),
    brandTint = Color(0xFFEAF4FA),           // ~12% brandDeep over white
    keyboardBackground = Color(0xFFECECEC),  // oklch(~0.935 0 0)
    key = Color(0xFFFFFFFF),
    keyForeground = Color(0xFF171717),
    keyAlt = Color(0xFFD6D6D6),              // oklch(~0.86 0 0)
)

val AulaaDarkPalette = AulaaPalette(
    background = Color(0xFF0A0A0A),          // oklch(0.145 0 0)
    foreground = Color(0xFFFAFAFA),          // oklch(0.985 0 0)
    card = Color(0xFF1A1A1A),                // oklch(0.205 0 0)
    cardForeground = Color(0xFFFAFAFA),
    muted = Color(0xFF2B2B2B),               // oklch(0.269 0 0)
    mutedForeground = Color(0xFFA3A3A3),     // oklch(0.708 0 0)
    secondary = Color(0xFF2B2B2B),
    border = Color(0x1AFFFFFF),              // oklch(1 0 0 / 10%)
    input = Color(0x26FFFFFF),               // oklch(1 0 0 / 15%)
    ring = Color(0xFF737373),                // oklch(0.556 0 0)
    primary = Color(0xFFE5E5E5),             // oklch(0.922 0 0)
    primaryForeground = Color(0xFF1A1A1A),
    brandDeep = BrandDeep,
    brandMid = BrandMid,
    brandLight = BrandLight,
    onBrand = Color(0xFFFFFFFF),
    brandTint = Color(0xFF13303D),           // low-chroma dark blue fill
    keyboardBackground = Color(0xFF151515),  // oklch(~0.17 0 0)
    key = Color(0xFF2B2B2B),                 // oklch(~0.27 0 0)
    keyForeground = Color(0xFFFAFAFA),
    keyAlt = Color(0xFF232323),              // oklch(~0.225 0 0)
)
