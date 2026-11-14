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

package dev.patrickgold.florisboard.app.settings.typing

import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.components.FlorisScreenNg
import dev.patrickgold.florisboard.app.components.ScrollableScreenColumn
import dev.patrickgold.florisboard.app.components.SegmentedListColumn
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.keyboard3.touch.ShiftKeyBehavior
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import org.florisboard.lib.compose.stringRes

@Composable
fun TypingScreen() {
    val prefs by FlorisPreferenceStore

    FlorisScreenNg(
        title = stringRes(R.string.settings__typing__title),
        previewFieldVisible = true,
    ) { contentPadding ->
        ScrollableScreenColumn(contentPadding) {
            SegmentedListColumn(title = stringRes(R.string.pref__typing_caps_group__title)) {
                val count = 3
                SwitchPreference(
                    prefs.typing.autoCapitalization,
                    shapes = ListItemDefaults.segmentedShapes(0, count),
                    title = stringRes(R.string.pref__correction__auto_capitalization__label),
                    summary = stringRes(R.string.pref__correction__auto_capitalization__summary),
                )
                SwitchPreference(
                    prefs.typing.rememberCapsLockState,
                    shapes = ListItemDefaults.segmentedShapes(1, count),
                    title = stringRes(R.string.pref__correction__remember_caps_lock_state__label),
                    summary = stringRes(R.string.pref__correction__remember_caps_lock_state__summary),
                )
                ListPreference(
                    prefs.typing.shiftKeyBehavior,
                    shapes = ListItemDefaults.segmentedShapes(2, count),
                    title = stringRes(R.string.pref__typing__shift_key_behavior__label),
                    entries = enumDisplayEntriesOf(ShiftKeyBehavior::class),
                )
            }

            SegmentedListColumn(title = stringRes(R.string.pref__correction__title)) {
                SwitchPreference(
                    prefs.correction.doubleSpacePeriod,
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    title = stringRes(R.string.pref__correction__double_space_period__label),
                    summary = stringRes(R.string.pref__correction__double_space_period__summary),
                )
            }
        }
    }
}
