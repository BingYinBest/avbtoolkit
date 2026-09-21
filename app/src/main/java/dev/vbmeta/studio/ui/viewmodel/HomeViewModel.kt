package dev.vbmeta.studio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vbmeta.studio.engine.ToolchainStatus
import dev.vbmeta.studio.templateApp
import dev.vbmeta.studio.ui.screen.home.HomeUiState
import dev.vbmeta.studio.ui.screen.home.ToolchainUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val toolchain = templateApp.toolchainManager
    private val jobsRepo = templateApp.jobsRepository
    private val keyManager = templateApp.keyManager

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            jobsRepo.jobs.collect { jobs ->
                _uiState.update {
                    it.copy(recentJobs = jobs.sortedByDescending { j -> j.createdAt }.take(5))
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = toolchain.status()
            val keyCount = keyManager.listKeys().size
            _uiState.update {
                it.copy(
                    toolchain = it.toolchain.copy(ready = status is ToolchainStatus.Ready),
                    keyCount = keyCount,
                )
            }
        }
    }

    fun initToolchain() {
        if (_uiState.value.toolchain.initializing) return
        viewModelScope.launch {
            _uiState.update { it.copy(toolchain = it.toolchain.copy(initializing = true)) }
            val ok = toolchain.ensureReady()
            val versions = if (ok) toolchain.selfCheck() else emptyMap()
            _uiState.update {
                it.copy(toolchain = ToolchainUiState(ready = ok, initializing = false, versions = versions))
            }
        }
    }

    fun selfCheck() {
        viewModelScope.launch {
            val versions = toolchain.selfCheck()
            _uiState.update { it.copy(toolchain = it.toolchain.copy(versions = versions)) }
        }
    }
}
