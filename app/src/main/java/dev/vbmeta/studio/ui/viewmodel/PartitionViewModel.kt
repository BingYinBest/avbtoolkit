package dev.vbmeta.studio.ui.viewmodel

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vbmeta.studio.engine.AvbTool
import dev.vbmeta.studio.engine.FecTool
import dev.vbmeta.studio.templateApp
import dev.vbmeta.studio.ui.screen.partition.PartitionUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class PartitionViewModel : ViewModel() {

    private val app = templateApp
    private val avb = AvbTool(app.toolchainManager)

    private val _uiState = MutableStateFlow(PartitionUiState())
    val uiState: StateFlow<PartitionUiState> = _uiState.asStateFlow()

    private fun copyToWork(uri: Uri, prefix: String): Pair<String, String>? {
        return try {
            val workDir = File(app.filesDir, "work").apply { mkdirs() }
            val name = queryDisplayName(uri) ?: "${prefix}_${System.currentTimeMillis()}.img"
            val workFile = File(workDir, "${System.currentTimeMillis()}_$name")
            app.contentResolver.openInputStream(uri)?.use { input ->
                workFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Pair(workFile.absolutePath, name)
        } catch (_: Exception) {
            null
        }
    }

    fun pickMiscImage(uri: Uri) {
        copyToWork(uri, "misc")?.let { (path, name) ->
            _uiState.update { it.copy(miscImagePath = path, miscImageName = name) }
        }
    }

    fun pickKernelImage(uri: Uri) {
        copyToWork(uri, "kernel")?.let { (path, name) ->
            _uiState.update { it.copy(kernelImagePath = path, kernelImageName = name) }
        }
    }

    fun slotDataChange(value: String) {
        _uiState.update { it.copy(slotData = value) }
    }

    fun hashtreeDisabledChange(value: Boolean) {
        _uiState.update { it.copy(hashtreeDisabled = value) }
    }

    fun setAbMetadata() {
        val image = _uiState.value.miscImagePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(running = true) }
            val sb = StringBuilder()
            val ok = runCatching {
                val exit = avb.setAbMetadata(image, _uiState.value.slotData) { sb.appendLine(it) }
                sb.appendLine("set_ab_metadata 退出码: $exit")
                exit == 0
            }.getOrDefault(false)
            _uiState.update {
                it.copy(running = false, result = sb.toString().ifBlank { if (ok) "完成 ✓" else "失败" })
            }
        }
    }

    fun calcKernelCmdline() {
        val image = _uiState.value.kernelImagePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(running = true) }
            val sb = StringBuilder()
            runCatching {
                avb.calculateKernelCmdline(image, hashtreeDisabled = _uiState.value.hashtreeDisabled) { sb.appendLine(it) }
            }
            _uiState.update { it.copy(running = false, result = sb.toString()) }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) {
            null
        }
    }
}