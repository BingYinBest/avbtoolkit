package dev.vbmeta.studio.ui.screen.home

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vbmeta.studio.R
import dev.vbmeta.studio.model.JobStatus
import dev.vbmeta.studio.ui.component.miuix.WarningCard
import dev.vbmeta.studio.ui.theme.LocalEnableBlur
import dev.vbmeta.studio.ui.util.BlurredBar
import dev.vbmeta.studio.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomePagerMiuix(
    state: HomeUiState,
    actions: HomeActions,
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
                    title = stringResource(R.string.app_name),
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
                        ToolchainHeroCard(state, actions)
                        QuickActionsCard(state, actions)
                        RecentJobsCard(state, actions)
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun ToolchainHeroCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val ready = state.toolchain.ready
    val iconColor = if (ready) Color(0xFF36D167) else Color(0xFFF72727)
    val containerColor = if (ready) Color(0xFFDFFAE4) else Color(0xFFF8E2E2)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = containerColor),
        showIndication = false,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 76.dp, y = 52.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(168.dp),
                    imageVector = if (ready) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Cancel,
                    tint = iconColor,
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, top = 24.dp, end = 160.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(
                            if (ready) R.string.home_toolchain_ready else R.string.home_toolchain_missing
                        ),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111111),
                    )
                    Text(
                        text = stringResource(R.string.home_key_count, state.keyCount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111111).copy(alpha = 0.72f),
                    )
                    if (state.toolchain.versions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            state.toolchain.versions.entries.take(4).forEach { (name, version) ->
                                Text(
                                    text = "$name $version",
                                    fontSize = 12.sp,
                                    color = Color(0xFF111111).copy(alpha = 0.6f),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.toolchain.initializing) {
                        Text(
                            text = stringResource(R.string.home_toolchain_initializing),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF111111).copy(alpha = 0.78f),
                        )
                    } else {
                        TextButton(text = stringResource(R.string.home_toolchain_init), onClick = actions.onInitToolchain)
                        TextButton(text = stringResource(R.string.home_toolchain_selfcheck), onClick = actions.onSelfCheck)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.home_quick_actions),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Bolt,
                    label = stringResource(R.string.home_quick_sign_boot),
                    onClick = { actions.onOpenPage(1) },
                )
                QuickActionItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Storage,
                    label = stringResource(R.string.home_quick_sign_system),
                    onClick = { actions.onOpenPage(1) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Verified,
                    label = stringResource(R.string.home_quick_verify),
                    onClick = { actions.onOpenPage(1) },
                )
                QuickActionItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.AutoFixHigh,
                    label = stringResource(R.string.home_quick_fec),
                    onClick = { actions.onOpenPage(1) },
                )
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Card(modifier = modifier, onClick = onClick, showIndication = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                tint = colorScheme.primary,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RecentJobsCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.home_recent_jobs),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            if (state.recentJobs.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_recent_empty),
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                state.recentJobs.forEach { job ->
                    BasicComponent(
                        title = job.displayName,
                        summary = "${job.partitionType.label} · ${job.status.label} · ${formatTime(job.createdAt)}",
                        onClick = { actions.onOpenPage(1) },
                    )
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
