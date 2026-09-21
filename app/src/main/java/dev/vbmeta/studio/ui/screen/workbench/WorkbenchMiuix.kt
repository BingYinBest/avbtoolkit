package dev.vbmeta.studio.ui.screen.workbench

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import dev.vbmeta.studio.model.ImageJob
import dev.vbmeta.studio.model.JobMode
import dev.vbmeta.studio.model.JobStatus
import dev.vbmeta.studio.model.SignConfig
import dev.vbmeta.studio.ui.component.miuix.EditText
import dev.vbmeta.studio.ui.component.miuix.WarningCard
import dev.vbmeta.studio.ui.theme.LocalEnableBlur
import dev.vbmeta.studio.ui.util.BlurredBar
import dev.vbmeta.studio.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val ALGORITHMS = listOf(
    "SHA256_RSA2048", "SHA256_RSA4096", "SHA256_RSA8192",
    "SHA512_RSA2048", "SHA512_RSA4096", "SHA512_RSA8192",
)

@Composable
fun WorkbenchPagerMiuix(
    state: WorkbenchUiState,
    actions: WorkbenchActions,
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
                    title = stringResource(R.string.tab_workbench),
                    actions = {
                        IconButton(onClick = actions.onPickImages, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                tint = colorScheme.onSurface,
                                contentDescription = stringResource(R.string.workbench_add),
                            )
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
                        if (!state.toolchainReady) {
                            WarningCard(stringResource(R.string.workbench_toolchain_missing))
                        }
                        state.lastError?.let { error ->
                            Text(
                                text = error,
                                fontSize = 13.sp,
                                color = Color(0xFFF72727),
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        PresetCard(state, actions)
                        if (state.running) {
                            Text(
                                text = stringResource(R.string.workbench_progress, state.queuePosition, state.queueTotal),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        if (state.jobs.isEmpty()) {
                            Text(
                                text = stringResource(R.string.workbench_empty),
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        } else {
                            state.jobs.forEach { job ->
                                JobCard(state, actions, job)
                            }
                        }
                        ActionRow(state, actions)
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    state: WorkbenchUiState,
    actions: WorkbenchActions,
) {
    val ids = state.jobs.filter { it.status != JobStatus.RUNNING }.map { it.id }
    Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = stringResource(R.string.workbench_preset_ksu),
                    onClick = { actions.onApplyPreset(JobPreset.KSU_SIGN, ids) },
                )
                TextButton(
                    text = stringResource(R.string.workbench_preset_gsi),
                    onClick = { actions.onApplyPreset(JobPreset.GSI_SIGN, ids) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    tint = colorScheme.primary,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(R.string.workbench_create_vbmeta),
                    fontSize = 13.sp,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                TextButton(text = stringResource(R.string.workbench_run), onClick = actions.onCreateVbmeta)
            }
        }
    }
}

@Composable
private fun JobCard(
    state: WorkbenchUiState,
    actions: WorkbenchActions,
    job: ImageJob,
) {
    val selected = job.id == state.selectedJobId
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) {
            CardDefaults.defaultColors(color = colorScheme.primaryContainer.copy(alpha = 0.35f))
        } else {
            CardDefaults.defaultColors()
        },
        onClick = { actions.onSelectJob(if (selected) null else job.id) },
        showIndication = true,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            BasicComponent(
                title = job.displayName,
                summary = "${job.partitionType.label} · ${formatSize(job.sizeBytes)} · ${job.status.label}",
                endActions = {
                    Text(
                        text = job.status.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor(job.status),
                    )
                },
                onClick = { actions.onSelectJob(if (selected) null else job.id) },
            )
            if (selected) {
                ConfigSection(state, actions, job)
                if (job.log.isNotBlank()) {
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
                        showIndication = false,
                    ) {
                        Text(
                            text = job.log,
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    if (job.status == JobStatus.SUCCESS) {
                        TextButton(text = stringResource(R.string.workbench_export), onClick = { actions.onExportJob(job) })
                    }
                    TextButton(
                        text = stringResource(R.string.workbench_delete),
                        onClick = { actions.onRemoveJob(job.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigSection(
    state: WorkbenchUiState,
    actions: WorkbenchActions,
    job: ImageJob,
) {
    val config = job.config
    fun update(transform: (SignConfig) -> SignConfig) {
        actions.onUpdateConfig(job.id, transform(config))
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OverlayDropdownPreference(
            title = stringResource(R.string.workbench_mode),
            summary = config.mode.label,
            items = JobMode.entries.map { it.label },
            selectedIndex = JobMode.entries.indexOfFirst { it == config.mode }.coerceAtLeast(0),
            onSelectedIndexChange = { index ->
                JobMode.entries.getOrNull(index)?.let { m -> update { cfg -> cfg.copy(mode = m) } }
            },
        )
        OverlayDropdownPreference(
            title = stringResource(R.string.workbench_key),
            summary = config.keyName ?: stringResource(R.string.workbench_no_key),
            items = listOf(stringResource(R.string.workbench_no_key)) + state.keys.map { it.name },
            selectedIndex = (listOf(null) + state.keys.map { it.name }).indexOf(config.keyName).coerceAtLeast(0),
            onSelectedIndexChange = { index ->
                update { cfg -> cfg.copy(keyName = (listOf(null) + state.keys.map { k -> k.name })[index]) }
            },
        )
        OverlayDropdownPreference(
            title = stringResource(R.string.workbench_algorithm),
            summary = config.algorithm,
            items = ALGORITHMS,
            selectedIndex = ALGORITHMS.indexOf(config.algorithm).coerceAtLeast(0),
            onSelectedIndexChange = { index ->
                ALGORITHMS.getOrNull(index)?.let { algo -> update { cfg -> cfg.copy(algorithm = algo) } }
            },
        )
        NumericConfigField(
            key = job.id,
            title = stringResource(R.string.workbench_rollback),
            initial = config.rollbackIndex,
            onCommit = { v -> update { cfg -> cfg.copy(rollbackIndex = v) } },
        )
        if (config.mode == JobMode.HASHTREE_FOOTER || config.mode == JobMode.FEC_ENCODE) {
            NumericConfigField(
                key = job.id,
                title = stringResource(R.string.workbench_fec_roots),
                initial = (config.fecNumRoots ?: 2).toLong(),
                onCommit = { v -> update { cfg -> cfg.copy(fecNumRoots = v.toInt()) } },
            )
        }
        if (config.mode == JobMode.HASH_FOOTER || config.mode == JobMode.HASHTREE_FOOTER) {
            EditText(
                title = stringResource(R.string.workbench_partition_name),
                value = config.partitionName ?: "",
                onValueChange = { v -> update { cfg -> cfg.copy(partitionName = v) } },
                textHint = job.partitionType.label,
            )
            NumericConfigField(
                key = job.id,
                title = stringResource(R.string.workbench_partition_size),
                initial = config.partitionSize ?: 0,
                onCommit = { v -> update { cfg -> cfg.copy(partitionSize = if (v > 0) v else null) } },
            )
        }
    }
}

@Composable
private fun NumericConfigField(
    key: String,
    title: String,
    initial: Long,
    onCommit: (Long) -> Unit,
) {
    var text by remember(key) { mutableStateOf(initial.toString()) }
    EditText(
        title = title,
        value = text,
        onValueChange = { input ->
            text = input.filter(Char::isDigit)
            input.toLongOrNull()?.let(onCommit)
        },
        textHint = initial.toString(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun ActionRow(
    state: WorkbenchUiState,
    actions: WorkbenchActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TextButton(
            text = if (state.running) stringResource(R.string.workbench_cancel) else stringResource(R.string.workbench_run),
            onClick = if (state.running) actions.onCancelRun else actions.onRunJobs,
        )
        TextButton(text = stringResource(R.string.workbench_clear_finished), onClick = actions.onClearFinished)
    }
}

private fun statusColor(status: JobStatus): Color = when (status) {
    JobStatus.SUCCESS -> Color(0xFF36D167)
    JobStatus.FAILED -> Color(0xFFF72727)
    JobStatus.RUNNING -> Color(0xFF3D9AF7)
    JobStatus.PENDING -> Color(0xFFB9B9B9)
    JobStatus.CANCELLED -> Color(0xFFB9B9B9)
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024) else "%.1f MB".format(mb)
}
