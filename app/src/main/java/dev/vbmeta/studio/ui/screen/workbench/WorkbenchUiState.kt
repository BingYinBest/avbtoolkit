package dev.vbmeta.studio.ui.screen.workbench

import android.net.Uri
import androidx.compose.runtime.Immutable
import dev.vbmeta.studio.engine.KeyEntry
import dev.vbmeta.studio.model.ImageJob
import dev.vbmeta.studio.model.SignConfig

enum class JobPreset(val label: String) {
    KSU_SIGN("KSU 自签"),
    GSI_SIGN("GSI + FEC"),
}

@Immutable
data class WorkbenchUiState(
    val jobs: List<ImageJob> = emptyList(),
    val selectedJobId: String? = null,
    val toolchainReady: Boolean = false,
    val keys: List<KeyEntry> = emptyList(),
    val running: Boolean = false,
    val queuePosition: Int = 0,
    val queueTotal: Int = 0,
    val lastError: String? = null,
)

@Immutable
data class WorkbenchActions(
    val onPickImages: () -> Unit,
    val onSelectJob: (String?) -> Unit,
    val onUpdateConfig: (String, SignConfig) -> Unit,
    val onRemoveJob: (String) -> Unit,
    val onRunJobs: () -> Unit,
    val onCancelRun: () -> Unit,
    val onExportJob: (ImageJob) -> Unit,
    val onCreateVbmeta: () -> Unit,
    val onClearFinished: () -> Unit,
    val onApplyPreset: (JobPreset, List<String>) -> Unit,
    /** 为指定任务选择一个附加文件（如追加 vbmeta 用的 vbmeta 镜像） */
    val onPickExtraImage: (String) -> Unit,
    /** 初始化工具链 */
    val onInitToolchain: () -> Unit,
)
