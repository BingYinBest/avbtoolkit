package dev.vbmeta.studio.ui.screen.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import dev.vbmeta.studio.R
import dev.vbmeta.studio.model.ImageJob
import dev.vbmeta.studio.model.JobMode
import dev.vbmeta.studio.model.JobStatus
import dev.vbmeta.studio.model.SignConfig
import dev.vbmeta.studio.ui.component.material.SegmentedDropdownItem

private val ALGORITHMS = listOf(
    "SHA256_RSA2048", "SHA256_RSA4096", "SHA256_RSA8192",
    "SHA512_RSA2048", "SHA512_RSA4096", "SHA512_RSA8192",
)

@Composable
fun WorkbenchPagerMaterial(
    state: WorkbenchUiState,
    actions: WorkbenchActions,
    bottomInnerPadding: Dp,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_workbench)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                actions = {
                    IconButton(onClick = actions.onPickImages) {
                        Icon(Icons.Default.Add, stringResource(R.string.workbench_add))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!state.toolchainReady) {
                        Text(
                            text = stringResource(R.string.workbench_toolchain_missing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    state.lastError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    PresetCard(state, actions)
                    if (state.running) {
                        Text(
                            text = stringResource(R.string.workbench_progress, state.queuePosition, state.queueTotal),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (state.jobs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.workbench_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    } else {
                        state.jobs.forEach { job ->
                            JobCard(state, actions, job)
                        }
                    }
                    ActionRow(state, actions)
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.onApplyPreset(JobPreset.KSU_SIGN, ids) }) {
                    Text(stringResource(R.string.workbench_preset_ksu))
                }
                OutlinedButton(onClick = { actions.onApplyPreset(JobPreset.GSI_SIGN, ids) }) {
                    Text(stringResource(R.string.workbench_preset_gsi))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.workbench_create_vbmeta),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = actions.onCreateVbmeta) {
                    Text(stringResource(R.string.workbench_run))
                }
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
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        onClick = { actions.onSelectJob(if (selected) null else job.id) },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${job.partitionType.label} · ${formatSize(job.sizeBytes)} · ${job.status.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Text(
                    text = job.status.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = statusColor(job.status),
                )
            }
            if (selected) {
                ConfigSection(state, actions, job)
                if (job.log.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            text = job.log,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (job.status == JobStatus.SUCCESS) {
                        Button(onClick = { actions.onExportJob(job) }) {
                            Text(stringResource(R.string.workbench_export))
                        }
                    }
                    TextButton(onClick = { actions.onRemoveJob(job.id) }) {
                        Text(stringResource(R.string.workbench_delete))
                    }
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

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SegmentedDropdownItem(
            icon = null,
            title = stringResource(R.string.workbench_mode),
            summary = config.mode.label,
            items = JobMode.entries.map { it.label },
            selectedIndex = JobMode.entries.indexOfFirst { it == config.mode }.coerceAtLeast(0),
            onItemSelected = { index ->
                JobMode.entries.getOrNull(index)?.let { mode -> update { it.copy(mode = mode) } }
            },
        )
        SegmentedDropdownItem(
            icon = null,
            title = stringResource(R.string.workbench_key),
            summary = config.keyName ?: stringResource(R.string.workbench_no_key),
            items = listOf(stringResource(R.string.workbench_no_key)) + state.keys.map { it.name },
            selectedIndex = (listOf(null) + state.keys.map { it.name }).indexOf(config.keyName).coerceAtLeast(0),
            onItemSelected = { index ->
                update { it.copy(keyName = (listOf(null) + state.keys.map { k -> k.name })[index]) }
            },
        )
        SegmentedDropdownItem(
            icon = null,
            title = stringResource(R.string.workbench_algorithm),
            summary = config.algorithm,
            items = ALGORITHMS,
            selectedIndex = ALGORITHMS.indexOf(config.algorithm).coerceAtLeast(0),
            onItemSelected = { index ->
                ALGORITHMS.getOrNull(index)?.let { algorithm -> update { it.copy(algorithm = algorithm) } }
            },
        )
        NumericConfigField(
            key = job.id,
            title = stringResource(R.string.workbench_rollback),
            initial = config.rollbackIndex,
            onCommit = { update { it.copy(rollbackIndex = it) } },
        )
        if (config.mode == JobMode.HASHTREE_FOOTER || config.mode == JobMode.FEC_ENCODE) {
            NumericConfigField(
                key = job.id,
                title = stringResource(R.string.workbench_fec_roots),
                initial = (config.fecNumRoots ?: 2).toLong(),
                onCommit = { update { it.copy(fecNumRoots = it.toInt()) } },
            )
        }
        if (config.mode == JobMode.HASH_FOOTER || config.mode == JobMode.HASHTREE_FOOTER) {
            OutlinedTextField(
                value = config.partitionName ?: "",
                onValueChange = { update { it.copy(partitionName = it) } },
                label = { Text(stringResource(R.string.workbench_partition_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NumericConfigField(
                key = job.id,
                title = stringResource(R.string.workbench_partition_size),
                initial = config.partitionSize ?: 0,
                onCommit = { update { it.copy(partitionSize = if (it > 0) it else null) } },
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
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter(Char::isDigit)
            input.toLongOrNull()?.let(onCommit)
        },
        label = { Text(title) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
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
        Button(
            onClick = if (state.running) actions.onCancelRun else actions.onRunJobs,
        ) {
            Text(if (state.running) stringResource(R.string.workbench_cancel) else stringResource(R.string.workbench_run))
        }
        OutlinedButton(onClick = actions.onClearFinished) {
            Text(stringResource(R.string.workbench_clear_finished))
        }
    }
}

private fun statusColor(status: JobStatus): Color = when (status) {
    JobStatus.SUCCESS -> Color(0xFF2E7D32)
    JobStatus.FAILED -> Color(0xFFC62828)
    JobStatus.RUNNING -> Color(0xFF1565C0)
    JobStatus.PENDING -> Color(0xFF757575)
    JobStatus.CANCELLED -> Color(0xFF757575)
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024) else "%.1f MB".format(mb)
}
