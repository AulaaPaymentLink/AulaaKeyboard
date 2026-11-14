/*
 * Copyright (C) 2026 Aulaa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.aulaa

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import co.aulaa.keyboard.payment.theme.AulaaTheme
import co.aulaa.keyboard.payment.ui.AulaaPairingContent
import dev.patrickgold.florisboard.app.components.FlorisScreenNg
import dev.patrickgold.florisboard.app.components.ScrollableScreenColumn
import dev.patrickgold.florisboard.aulaaApi
import dev.patrickgold.florisboard.aulaaPairingStore

@Composable
fun AulaaScreen(initialPairingCode: String? = null) {
    val context = LocalContext.current
    val api by context.aulaaApi()
    val pairingStore by context.aulaaPairingStore()
    val isDark = isSystemInDarkTheme()

    FlorisScreenNg(
        title = "Aulaa Payment Link",
        previewFieldVisible = true,
    ) { contentPadding ->
        ScrollableScreenColumn(contentPadding) {
            AulaaTheme(darkTheme = isDark) {
                AulaaPairingContent(
                    api = api,
                    pairingStore = pairingStore,
                    initialPairingCode = initialPairingCode,
                )
            }
        }
    }
}
