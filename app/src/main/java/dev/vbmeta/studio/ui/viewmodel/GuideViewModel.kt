package dev.vbmeta.studio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vbmeta.studio.templateApp
import dev.vbmeta.studio.ui.screen.guide.GuideArticle
import dev.vbmeta.studio.ui.screen.guide.GuideUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GuideViewModel : ViewModel() {

    private val articles = listOf(
        GuideArticle(
            title = "KernelSU / 自编译内核自签",
            summary = "为 boot、vendor_boot、init_boot 添加哈希 footer 并生成禁验 vbmeta",
            assetPath = "guide/ksu.md",
        ),
        GuideArticle(
            title = "GSI / 厂商镜像签名",
            summary = "为 system、vendor 等文件系统镜像添加哈希树与 FEC 纠错数据",
            assetPath = "guide/gsi.md",
        ),
        GuideArticle(
            title = "AVB 概念速览",
            summary = "vbmeta、descriptor、dm-verity、FEC、chain partition 是什么",
            assetPath = "guide/concepts.md",
        ),
    )

    private val _uiState = MutableStateFlow(GuideUiState(articles = articles))
    val uiState: StateFlow<GuideUiState> = _uiState.asStateFlow()

    init {
        load(0)
    }

    fun select(index: Int) {
        if (index != _uiState.value.selectedIndex) load(index)
    }

    private fun load(index: Int) {
        viewModelScope.launch {
            val article = articles.getOrNull(index) ?: return@launch
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    templateApp.assets.open(article.assetPath).bufferedReader().use { it.readText() }
                }.getOrNull()
            }
            _uiState.update { it.copy(selectedIndex = index, content = content) }
        }
    }
}
