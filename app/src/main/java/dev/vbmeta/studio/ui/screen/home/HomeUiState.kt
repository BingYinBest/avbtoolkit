package dev.vbmeta.studio.ui.screen.home

import androidx.compose.runtime.Immutable
import dev.vbmeta.studio.model.ImageJob

@Immutable
data class ToolchainUiState(
    val ready: Boolean = false,
    val initializing: Boolean = false,
    val versions: Map<String, String> = emptyMap(),
)

@Immutable
data class HomeUiState(
    val toolchain: ToolchainUiState = ToolchainUiState(),
    val recentJobs: List<ImageJob> = emptyList(),
    val keyCount: Int = 0,
)

@Immutable
data class HomeActions(
    val onInitToolchain: () -> Unit,
    val onSelfCheck: () -> Unit,
    val onOpenPage: (Int) -> Unit,
)
