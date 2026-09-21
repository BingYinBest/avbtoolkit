package dev.vbmeta.studio.ui.viewmodel

import androidx.compose.runtime.Immutable
import dev.vbmeta.studio.ui.UiMode
import dev.vbmeta.studio.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val uiMode: UiMode,
)
