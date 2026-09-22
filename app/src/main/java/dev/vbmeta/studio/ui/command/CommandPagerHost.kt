package dev.vbmeta.studio.ui.command

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vbmeta.studio.templateApp
import dev.vbmeta.studio.ui.LocalUiMode
import dev.vbmeta.studio.ui.UiMode
import dev.vbmeta.studio.ui.viewmodel.CommandViewModel
import java.io.File

/**
 * 统一命令列表宿主：命令列表 ↔ 命令表单切换；文件选择/导出/复制由这里统一处理。
 */
@Composable
fun CommandPagerHost(
    title: String,
    commands: List<CommandDef>,
    bottomInnerPadding: Dp,
    withKeyHeader: Boolean = false,
) {
    val viewModel = viewModel<CommandViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var pickKey by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = pickKey
        if (uri != null && key != null) viewModel.pickFileArg(key, uri)
        pickKey = null
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) viewModel.exportOutput(uri)
    }

    val actions = CommandListActions(
        onSelect = viewModel::select,
        onBack = viewModel::dismiss,
        onFormChange = viewModel::updateForm,
        onPickFile = { key ->
            pickKey = key
            filePicker.launch("*/*")
        },
        onRun = viewModel::run,
        onExport = { exportLauncher.launch("output.bin") },
        onCopyResult = {
            if (state.result.isNotBlank()) {
                clipboard.setText(AnnotatedString(state.result))
                android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
            }
        },
    )

    val header: (@Composable () -> Unit)? = if (withKeyHeader) {
        {
            if (LocalUiMode.current == UiMode.Miuix) {
                KeyManagerHeaderMiuix()
            } else {
                KeyManagerHeaderMaterial()
            }
        }
    } else null

    when (LocalUiMode.current) {
        UiMode.Miuix -> CommandListMiuix(title, commands, state, actions, bottomInnerPadding, header)
        UiMode.Material -> CommandListMaterial(title, commands, state, actions, bottomInnerPadding, header)
    }
}

@Composable
private fun KeyManagerHeaderMiuix() {
    val keyManager = templateApp.keyManager
    var keys by remember { mutableStateOf(keyManager.listKeys()) }
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val tmp = File(context.cacheDir, "import_key.pem")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                keyManager.import("imported-${System.currentTimeMillis() % 10000}", tmp.absolutePath)
            }
            keys = keyManager.listKeys()
        }
    }
    top.yukonga.miuix.kmp.basic.Card(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), showIndication = false) {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.padding(vertical = 4.dp),
        ) {
            top.yukonga.miuix.kmp.basic.Text(
                text = "密钥库",
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (keys.isEmpty()) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = "暂无密钥，先生成一把（RSA 4096）",
                    fontSize = 13.sp,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            } else {
                keys.forEach { key ->
                    top.yukonga.miuix.kmp.basic.BasicComponent(
                        title = key.name,
                        summary = "${key.bits} bit",
                        onClick = { },
                    )
                }
            }
            androidx.compose.foundation.layout.Row(modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp)) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "生成 4096",
                    onClick = {
                        keyManager.generate("avb-key-${System.currentTimeMillis() % 100000}", 4096)
                        keys = keyManager.listKeys()
                    },
                )
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "导入",
                    onClick = { importLauncher.launch("*/*") },
                )
            }
        }
    }
}

@Composable
private fun KeyManagerHeaderMaterial() {
    val keyManager = templateApp.keyManager
    var keys by remember { mutableStateOf(keyManager.listKeys()) }
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val tmp = File(context.cacheDir, "import_key.pem")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                keyManager.import("imported-${System.currentTimeMillis() % 10000}", tmp.absolutePath)
            }
            keys = keyManager.listKeys()
        }
    }
    androidx.compose.material3.Card(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Text(
                text = "密钥库",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            if (keys.isEmpty()) {
                androidx.compose.material3.Text(
                    text = "暂无密钥，先生成一把（RSA 4096）",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                )
            } else {
                keys.forEach { key ->
                    androidx.compose.material3.Text(
                        text = "${key.name}（${key.bits} bit）",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.OutlinedButton(onClick = {
                    keyManager.generate("avb-key-${System.currentTimeMillis() % 100000}", 4096)
                    keys = keyManager.listKeys()
                }) {
                    androidx.compose.material3.Text("生成 4096")
                }
                androidx.compose.material3.OutlinedButton(onClick = { importLauncher.launch("*/*") }) {
                    androidx.compose.material3.Text("导入")
                }
            }
        }
    }
}