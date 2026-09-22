package dev.vbmeta.studio.ui.command

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vbmeta.studio.R
import dev.vbmeta.studio.ui.component.miuix.EditText
import dev.vbmeta.studio.ui.theme.LocalEnableBlur
import dev.vbmeta.studio.ui.util.BlurredBar
import dev.vbmeta.studio.ui.util.rememberBlurBackdrop
import dev.vbmeta.studio.ui.viewmodel.CommandUiState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

data class CommandListActions(
    val onSelect: (CommandDef) -> Unit,
    val onBack: () -> Unit,
    val onFormChange: (String, String) -> Unit,
    val onPickFile: (String) -> Unit,
    val onRun: () -> Unit,
    val onExport: () -> Unit,
    val onCopyResult: () -> Unit,
)

@Composable
fun CommandListMiuix(
    title: String,
    commands: List<CommandDef>,
    state: CommandUiState,
    actions: CommandListActions,
    bottomInnerPadding: Dp,
    header: (@Composable () -> Unit)? = null,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.app_name).let { title },
                    navigationIcon = {
                        if (state.command != null) {
                            IconButton(onClick = actions.onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    tint = colorScheme.onSurface,
                                    contentDescription = "返回",
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val cmd = state.command
                        if (cmd == null) {
                            header?.invoke()
                            commands.forEach { c ->
                                CommandCardMiuix(c, onClick = { actions.onSelect(c) })
                            }
                        } else {
                            CommandFormMiuix(cmd, state, actions)
                        }
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun CommandCardMiuix(
    cmd: CommandDef,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (cmd.unsupported) {
            top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(color = colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
        } else {
            top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors()
        },
        onClick = onClick,
        showIndication = true,
    ) {
        BasicComponent(
            title = cmd.title,
            summary = cmd.description,
            onClick = onClick,
        )
    }
}

@Composable
private fun CommandFormMiuix(
    cmd: CommandDef,
    state: CommandUiState,
    actions: CommandListActions,
) {
    Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = cmd.title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            cmd.args.forEach { arg ->
                val value = state.form[arg.key] ?: ""
                when (arg.kind) {
                    ArgKind.FILE -> BasicComponent(
                        title = arg.label,
                        summary = value.substringAfterLast('/').ifEmpty { arg.hint.ifEmpty { "点击选择文件" } },
                        endActions = {
                            Icon(
                                imageVector = Icons.Rounded.FileOpen,
                                tint = colorScheme.onSurface,
                                contentDescription = null,
                            )
                        },
                        onClick = { actions.onPickFile(arg.key) },
                    )

                    ArgKind.BOOL -> SwitchPreference(
                        title = arg.label,
                        checked = value == "true",
                        onCheckedChange = { actions.onFormChange(arg.key, it.toString()) },
                    )

                    ArgKind.NUMBER -> EditText(
                        title = arg.label,
                        value = value,
                        onValueChange = { actions.onFormChange(arg.key, it.filter(Char::isDigit)) },
                        textHint = arg.hint,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    ArgKind.TEXT -> EditText(
                        title = arg.label,
                        value = value,
                        onValueChange = { actions.onFormChange(arg.key, it) },
                        textHint = arg.hint,
                    )
                }
            }
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                TextButton(
                    text = stringResource(R.string.verify_run),
                    onClick = actions.onRun,
                )
                if (state.outputPath != null) {
                    TextButton(text = stringResource(R.string.workbench_export), onClick = actions.onExport)
                }
                if (state.result.isNotBlank()) {
                    TextButton(text = "复制", onClick = actions.onCopyResult)
                }
            }
            if (state.result.isNotBlank()) {
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
                    showIndication = false,
                ) {
                    Text(
                        text = state.result,
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}