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

package dev.patrickgold.florisboard.app.setup

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.NoBackupPreferenceStore
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.components.LocalPreviewFieldController
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.lib.util.launchActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

suspend fun Flow<Boolean>.awaitTransition() {
    var previous: Boolean? = null
    first { current ->
        val becameTrue = previous == false && current
        previous = current
        becameTrue
    }
}

private sealed class Steps(val id: Int) {
    data object Welcome : Steps(id = 0)
    data object EnableIme : Steps(id = 1)
    data object SelectIme : Steps(id = 2)
    data object FinishUp: Steps(id = 3)
}

@Composable
fun SetupScreen(hasRestoredBackup: Boolean) {
    val noBackupPrefs by NoBackupPreferenceStore
    val context = LocalContext.current
    val navController = LocalNavController.current
    val previewFieldController = LocalPreviewFieldController.current
    val scope = rememberCoroutineScope()

    val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled()
    val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected()

    SideEffect {
        previewFieldController.isVisible = false
    }

    var welcomeSeen by rememberSaveable { mutableStateOf(hasRestoredBackup) }

    LaunchedEffect(Unit) {
        InputMethodUtils.observeIsFlorisboardEnabledFlow(context)
            .awaitTransition()
        context.launchActivity(FlorisAppActivity::class) {
            it.flags = (Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                or Intent.FLAG_ACTIVITY_SINGLE_TOP
                or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    val steps = remember(
        welcomeSeen,
        isFlorisBoardEnabled,
        isFlorisBoardSelected,
    ) {
        listOfNotNull(
            SetupStep(
                id = Steps.Welcome.id,
                required = hasRestoredBackup,
                satisfied = welcomeSeen,
                icon = Icons.Default.Keyboard,
                shape = SetupShapes.Welcome,
                title = { stringRes(R.string.setup__welcome__title) },
                summary = { stringRes(R.string.setup__welcome__summary) },
                primaryLabel = { stringRes(R.string.setup__welcome__get_started_btn) },
                onPrimary = {
                    welcomeSeen = true
                    next()
                },
                showProgress = false,
            ),
            SetupStep(
                id = Steps.EnableIme.id,
                required = true,
                satisfied = isFlorisBoardEnabled,
                icon = Icons.Default.Keyboard,
                shape = SetupShapes.Enable,
                title = { stringRes(R.string.setup__enable_ime__title) },
                summary = { stringRes(R.string.setup__enable_ime__summary) },
                description = { stringRes(R.string.setup__enable_ime__description) },
                primaryLabel = { stringRes(R.string.setup__enable_ime__open_settings_btn) },
                onPrimary = { InputMethodUtils.showImeEnablerActivity(context) },
            ),
            SetupStep(
                id = Steps.SelectIme.id,
                required = true,
                satisfied = isFlorisBoardSelected,
                icon = Icons.Default.SwapHoriz,
                shape = SetupShapes.Select,
                title = { stringRes(R.string.setup__select_ime__title) },
                summary = { stringRes(R.string.setup__select_ime__summary) },
                description = { stringRes(R.string.setup__select_ime__description) },
                primaryLabel = { stringRes(R.string.setup__select_ime__switch_keyboard_btn) },
                onPrimary = { InputMethodUtils.showImePicker(context) },
            ),
            SetupStep(
                id = Steps.FinishUp.id,
                required = false,
                satisfied = false,
                icon = Icons.Default.Check,
                shape = SetupShapes.Finish,
                title = { stringRes(R.string.setup__finish_up__title) },
                summary = { stringRes(R.string.setup__finish_up__summary) },
                primaryLabel = { stringRes(R.string.setup__finish_up__finish_btn) },
                onPrimary = {
                    scope.launch { noBackupPrefs.isImeSetUp.set(true) }
                    navController.navigate(Routes.Settings.Home) {
                        popUpTo(Routes.Setup.Screen::class) { inclusive = true }
                    }
                },
                showProgress = false,
            ),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { contentPadding ->
        SetupFlowLayout(
            steps = steps,
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
        )
    }
}
