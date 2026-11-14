/*
 * Copyright (C) 2021-2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.nlp.NlpInlineAutofill
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.verticalTween
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

const val AnimationDuration = 200

val VerticalEnterTransition = EnterTransition.verticalTween(AnimationDuration)
val VerticalExitTransition = ExitTransition.verticalTween(AnimationDuration)

/**
 * Clean minimal top bar (inspired by BCA Keyboard):
 * - Left: "Open Aulaa Keyboard >" shortcut (or Android inline autofill suggestions when active)
 * - Right: Single Settings (⚙) icon button
 */
@Composable
fun Smartbar() {
    val inlineSuggestions by NlpInlineAutofill.suggestions.collectAsState()
    val shouldShowInlineSuggestionsUi = AndroidVersion.ATLEAST_API30_R && inlineSuggestions.isNotEmpty()

    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val fgColor = windowStyle.foreground().let { c ->
        if (c.isUnspecified || c.alpha == 0f) Color(0xFFE4E4E7) else c
    }

    SnyggRow(
        elementName = FlorisImeUi.Smartbar.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (shouldShowInlineSuggestionsUi) {
                InlineSuggestionsUi(inlineSuggestions)
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            FlorisImeService.launchAulaaPairing()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Open Aulaa Keyboard",
                        color = fgColor.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = fgColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        SnyggIconButton(
            elementName = FlorisImeUi.SmartbarActionKey.elementName,
            onClick = {
                FlorisImeService.launchSettings()
            },
            modifier = Modifier
                .sizeIn(maxHeight = FlorisImeSizing.smartbarHeight)
                .aspectRatio(1f),
        ) {
            SnyggIcon(
                imageVector = Icons.Default.Settings,
            )
        }
    }
}
