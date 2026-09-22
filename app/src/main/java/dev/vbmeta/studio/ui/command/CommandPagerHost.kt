package dev.vbmeta.studio.ui.command

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vbmeta.studio.templateApp
import dev.vbmeta.studio.ui.LocalUiMode
import dev.vbmeta.studio.ui.UiMode
import dev.vbmeta.studio.ui.viewmodel.CommandViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
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

    // 二级表单页优先拦截返回键：关闭表单而非切换 pager tab
    if (state.command != null) {
        BackHandler {
            viewModel.dismiss()
        }
    }

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
    val scope = rememberCoroutineScope()
    var keys by remember { mutableStateOf(keyManager.listKeys()) }
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val tmp = File(context.cacheDir, "import_key.pem")
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    keyManager.import("imported-${System.currentTimeMillis() % 10000}", tmp.absolutePath)
                }
            }
            keys = keyManager.listKeys()
        }
    }
    MiuixCard(modifier = Modifier.fillMaxWidth(), showIndication = false) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            MiuixText(
                text = "密钥库",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (keys.isEmpty()) {
                MiuixText(
                    text = "暂无密钥，先生成一把（RSA 4096）",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            } else {
                keys.forEach { key ->
                    BasicComponent(
                        title = key.name,
                        summary = "${key.bits} bit",
                        onClick = { },
                    )
                }
            }
            Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                MiuixTextButton(
                    text = "生成 4096",
                    onClick = {
                        scope.launch {
                            keyManager.generate("avb-key-${System.currentTimeMillis() % 100000}", 4096)
                        }
                        keys = keyManager.listKeys()
                    },
                )
                MiuixTextButton(
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
    val scope = rememberCoroutineScope()
    var keys by remember { mutableStateOf(keyManager.listKeys()) }
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val tmp = File(context.cacheDir, "import_key.pem")
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    keyManager.import("imported-${System.currentTimeMillis() % 10000}", tmp.absolutePath)
                }
            }
            keys = keyManager.listKeys()
        }
    }
    MaterialCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MaterialText(
                text = "密钥库",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (keys.isEmpty()) {
                MaterialText(
                    text = "暂无密钥，先生成一把（RSA 4096）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                keys.forEach { key ->
                    MaterialText(
                        text = "${key.name}（${key.bits} bit）",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        keyManager.generate("avb-key-${System.currentTimeMillis() % 100000}", 4096)
                    }
                    keys = keyManager.listKeys()
                }) {
                    MaterialText("生成 4096")
                }
                OutlinedButton(onClick = { importLauncher.launch("*/*") }) {
                    MaterialText("导入")
                }
            }
        }
    }
}