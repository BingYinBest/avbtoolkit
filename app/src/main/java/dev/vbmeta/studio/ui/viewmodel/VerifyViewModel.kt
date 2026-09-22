package dev.vbmeta.studio.ui.viewmodel

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vbmeta.studio.engine.AvbTool
import dev.vbmeta.studio.templateApp
import dev.vbmeta.studio.ui.screen.verify.VerifyMode
import dev.vbmeta.studio.ui.screen.verify.VerifyUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class VerifyViewModel : ViewModel() {

    private val app = templateApp
    private val avb = AvbTool(app.toolchainManager)

    private val _uiState = MutableStateFlow(VerifyUiState())
    val uiState: StateFlow<VerifyUiState> = _uiState.asStateFlow()

    fun pickImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val workDir = File(app.filesDir, "work").apply { mkdirs() }
            val name = queryDisplayName(uri) ?: "image_${System.currentTimeMillis()}.img"
            val workFile = File(workDir, "${System.currentTimeMillis()}_$name")
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    workFile.outputStream().use { output -> input.copyTo(output) }
                }
                _uiState.update {
                    it.copy(imagePath = workFile.absolutePath, imageName = name, result = "")
                }
            } catch (_: Exception) {
            }
        }
    }

    fun selectMode(mode: VerifyMode) {
        _uiState.update { it.copy(mode = mode, result = "") }
    }

    fun run() {
        val image = _uiState.value.imagePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(running = true, result = "") }
            val sb = StringBuilder()
            val onLine: (String) -> Unit = { sb.appendLine(it) }
            runCatching {
                when (_uiState.value.mode) {
                    VerifyMode.INFO -> {
                        val info = avb.infoImage(image)
                        if (info.hasFooter) {
                            sb.appendLine("Footer version: ${info.footerVersion}")
                            sb.appendLine("算法: ${info.algorithm}")
                            sb.appendLine("Rollback Index: ${info.rollbackIndex}")
                            sb.appendLine("Flags: ${info.flags}")
                            sb.appendLine("镜像大小: ${info.imageSize}")
                            sb.appendLine("descriptors: ${info.descriptors.joinToString()}")
                        } else {
                            sb.appendLine("未检测到 AVB footer")
                        }
                    }

                    VerifyMode.VERIFY -> {
                        val exit = avb.verifyImage(image, _uiState.value.keyPath, onLine)
                        sb.appendLine(if (exit == 0) "验证通过 ✓" else "验证失败（退出码 $exit）")
                    }

                    VerifyMode.PRINT_DIGESTS -> {
                        val exit = avb.printPartitionDigests(image, onLine)
                        if (exit != 0) sb.appendLine("print_partition_digests 退出码: $exit")
                    }

                    VerifyMode.CALC_DIGEST -> {
                        val exit = avb.calculateVbmetaDigest(image, onLine = onLine)
                        if (exit != 0) sb.appendLine("calculate_vbmeta_digest 退出码: $exit")
                    }
                }
            }.onFailure { e ->
                sb.appendLine("异常: ${e.message}")
            }
            _uiState.update { it.copy(running = false, result = sb.toString()) }
        }
    }

    fun copyResult() {
        // 结果复制由 UI 层通过 ClipboardManager 处理
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