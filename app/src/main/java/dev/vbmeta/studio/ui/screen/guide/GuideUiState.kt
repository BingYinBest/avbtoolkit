package dev.vbmeta.studio.ui.screen.guide

import androidx.compose.runtime.Immutable

@Immutable
data class GuideArticle(
    val title: String,
    val summary: String,
    val assetPath: String,
)

@Immutable
data class GuideUiState(
    val articles: List<GuideArticle> = emptyList(),
    val selectedIndex: Int = 0,
    val content: String? = null,
)

@Immutable
data class GuideActions(
    val onSelect: (Int) -> Unit,
)
