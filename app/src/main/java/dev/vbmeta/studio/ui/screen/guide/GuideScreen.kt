package dev.vbmeta.studio.ui.screen.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vbmeta.studio.ui.LocalUiMode
import dev.vbmeta.studio.ui.UiMode
import dev.vbmeta.studio.ui.navigation3.Navigator
import dev.vbmeta.studio.ui.viewmodel.GuideViewModel

@Composable
fun GuidePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
) {
    val viewModel = viewModel<GuideViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val actions = GuideActions(
        onSelect = viewModel::select,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> GuidePagerMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> GuidePagerMaterial(uiState, actions, bottomInnerPadding)
    }
}
