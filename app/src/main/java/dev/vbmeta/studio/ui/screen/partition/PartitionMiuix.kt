package dev.vbmeta.studio.ui.screen.partition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vbmeta.studio.R
import dev.vbmeta.studio.ui.component.miuix.EditText
import dev.vbmeta.studio.ui.theme.LocalEnableBlur
import dev.vbmeta.studio.ui.util.BlurredBar
import dev.vbmeta.studio.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
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

@Composable
fun PartitionPagerMiuix(
    state: PartitionUiState,
    actions: PartitionActions,
    bottomInnerPadding: Dp,
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
                    title = stringResource(R.string.partition_title),
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
                        // A/B 元数据
                        Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = stringResource(R.string.partition_set_ab),
                                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                BasicComponent(
                                    title = state.miscImageName ?: stringResource(R.string.partition_misc_image),
                                    summary = state.miscImagePath?.substringAfterLast('/') ?: "",
                                    onClick = actions.onPickMiscImage,
                                )
                                EditText(
                                    title = stringResource(R.string.partition_slot_data),
                                    value = state.slotData,
                                    onValueChange = actions.onSlotDataChange,
                                    textHint = "boot,slot=0",
                                )
                                TextButton(text = stringResource(R.string.verify_run), onClick = actions.onSetAbMetadata)
                            }
                        }
                        // 内核命令行
                        Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = stringResource(R.string.partition_kernel_cmdline),
                                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                BasicComponent(
                                    title = state.kernelImageName ?: stringResource(R.string.verify_select_image),
                                    summary = state.kernelImagePath?.substringAfterLast('/') ?: "",
                                    onClick = actions.onPickKernelImage,
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.partition_hashtree_disabled),
                                    checked = state.hashtreeDisabled,
                                    onCheckedChange = actions.onHashtreeDisabledChange,
                                )
                                TextButton(text = stringResource(R.string.verify_run), onClick = actions.onCalcKernelCmdline)
                            }
                        }
                        if (state.result.isNotBlank()) {
                            Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
                                Text(
                                    text = state.result,
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}