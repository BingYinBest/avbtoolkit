package dev.vbmeta.studio.ui.screen.verify

import androidx.compose.runtime.Immutable

enum class VerifyMode(val label: String) {
    INFO("查看信息 (info_image)"),
    VERIFY("验证 (verify_image)"),
    PRINT_DIGESTS("分区摘要 (print_partition_digests)"),
    CALC_DIGEST("vbmeta 摘要 (calculate_vbmeta_digest)"),
}

@Immutable
data class VerifyUiState(
    val imagePath: String? = null,
    val imageName: String? = null,
    val mode: VerifyMode = VerifyMode.INFO,
    val keyPath: String? = null,
    val running: Boolean = false,
    val result: String = "",
)

@Immutable
data class VerifyActions(
    val onPickImage: () -> Unit,
    val onSelectMode: (VerifyMode) -> Unit,
    val onRun: () -> Unit,
    val onCopyResult: () -> Unit,
)