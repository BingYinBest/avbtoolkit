package dev.vbmeta.studio.ui.viewmodel

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vbmeta.studio.engine.AvbTool
import dev.vbmeta.studio.engine.FecTool
import dev.vbmeta.studio.engine.ToolchainStatus
import dev.vbmeta.studio.model.ImageDetector
import dev.vbmeta.studio.model.ImageJob
import dev.vbmeta.studio.model.JobMode
import dev.vbmeta.studio.model.JobStatus
import dev.vbmeta.studio.model.PartitionType
import dev.vbmeta.studio.model.SignConfig
import dev.vbmeta.studio.templateApp
import dev.vbmeta.studio.ui.screen.workbench.JobPreset
import dev.vbmeta.studio.ui.screen.workbench.WorkbenchUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class WorkbenchViewModel : ViewModel() {

    private val jobsRepo = templateApp.jobsRepository
    private val toolchain = templateApp.toolchainManager
    private val keyManager = templateApp.keyManager
    private val app = templateApp

    private var runJob: Job? = null
    private var cancelRequested = false

    private val _uiState = MutableStateFlow(WorkbenchUiState())
    val uiState: StateFlow<WorkbenchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            jobsRepo.jobs.collect { jobs ->
                _uiState.update {
                    it.copy(
                        jobs = jobs,
                        toolchainReady = toolchain.status() is ToolchainStatus.Ready,
                    )
                }
            }
        }
        refreshKeys()
    }

    fun refreshKeys() {
        _uiState.update { it.copy(keys = keyManager.listKeys()) }
    }

    fun refreshToolchain() {
        _uiState.update { it.copy(toolchainReady = toolchain.status() is ToolchainStatus.Ready) }
    }

    fun clearError() {
        _uiState.update { it.copy(lastError = null) }
    }

    /** SAF 选择的镜像 → 拷贝到工作目录、识别分区类型、入队。 */
    fun onImagesPicked(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val workDir = File(app.filesDir, "work").apply { mkdirs() }
            for (uri in uris) {
                try {
                    val name = queryDisplayName(uri) ?: "image_${System.currentTimeMillis()}.img"
                    val workFile = File(workDir, "${System.currentTimeMillis()}_$name")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        workFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: continue
                    val type = ImageDetector.detect(workFile, name)
                    val job = ImageJob(
                        displayName = name,
                        srcUri = uri.toString(),
                        workPath = workFile.absolutePath,
                        partitionType = type,
                        sizeBytes = workFile.length(),
                        config = SignConfig(
                            mode = when (type) {
                                PartitionType.SYSTEM, PartitionType.VENDOR, PartitionType.PRODUCT -> JobMode.HASHTREE_FOOTER
                                else -> JobMode.HASH_FOOTER
                            },
                            partitionSize = alignToBlock(workFile.length()),
                            partitionName = ImageDetector.defaultPartitionName(type),
                        ),
                    )
                    jobsRepo.add(job)
                    _uiState.update { it.copy(selectedJobId = job.id) }
                } catch (_: Exception) {
                    // 单个文件导入失败不影响队列
                }
            }
        }
    }

    fun selectJob(jobId: String?) {
        _uiState.update { it.copy(selectedJobId = jobId) }
    }

    fun updateConfig(jobId: String, config: SignConfig) {
        val job = jobsRepo.jobs.value.find { it.id == jobId } ?: return
        if (job.status == JobStatus.RUNNING) return
        jobsRepo.update(job.copy(config = config))
    }

    fun removeJob(jobId: String) {
        val job = jobsRepo.jobs.value.find { it.id == jobId } ?: return
        if (job.status == JobStatus.RUNNING) return
        deleteWorkFile(job)
        jobsRepo.remove(jobId)
        _uiState.update { if (it.selectedJobId == jobId) it.copy(selectedJobId = null) else it }
    }

    fun applyPreset(preset: JobPreset, jobIds: List<String>) {
        jobsRepo.jobs.value.filter { it.id in jobIds && it.status != JobStatus.RUNNING }.forEach { job ->
            val config = when (preset) {
                JobPreset.KSU_SIGN -> job.config.copy(
                    mode = JobMode.HASH_FOOTER,
                    algorithm = "SHA256_RSA4096",
                    fecNumRoots = null,
                )

                JobPreset.GSI_SIGN -> job.config.copy(
                    mode = JobMode.HASHTREE_FOOTER,
                    algorithm = "SHA256_RSA4096",
                    fecNumRoots = 2,
                )
            }
            jobsRepo.update(job.copy(config = config))
        }
    }

    /** 生成禁验 vbmeta（flags=2，algorithm NONE）。 */
    fun createVbmeta() {
        if (runJob?.isActive == true) return
        runJob = viewModelScope.launch(Dispatchers.IO) {
            val ok = toolchain.ensureReady()
            if (!ok) return@launch
            val avb = AvbTool(toolchain)
            val dir = File(app.filesDir, "work").apply { mkdirs() }
            val out = File(dir, "vbmeta-disable.img")
            val log = StringBuilder()
            val exit = try {
                avb.makeVbmetaImage(
                    AvbTool.VbmetaParams(
                        output = out.absolutePath,
                        keyPath = null,
                        algorithm = "NONE",
                        flags = 2, // FLAG_VERIFICATION_DISABLED
                        paddingSize = 4096,
                    ),
                    onLine = { log.appendLine(it) },
                )
            } catch (e: Exception) {
                log.appendLine("异常: ${e.message}")
                -1
            }
            log.appendLine("make_vbmeta_image 退出码: $exit")
            if (exit == 0) {
                val job = ImageJob(
                    displayName = "vbmeta-disable.img",
                    srcUri = "",
                    workPath = out.absolutePath,
                    partitionType = PartitionType.VBMETA,
                    sizeBytes = out.length(),
                    config = SignConfig(mode = JobMode.INFO),
                    status = JobStatus.SUCCESS,
                    log = log.toString(),
                    finishedAt = System.currentTimeMillis(),
                )
                jobsRepo.add(job)
                _uiState.update { it.copy(selectedJobId = job.id) }
            } else {
                _uiState.update { it.copy(lastError = log.toString()) }
            }
            runJob = null
        }
    }

    /** 串行执行队列中所有排队任务。 */
    fun runJobs() {
        if (runJob?.isActive == true) return
        val queue = jobsRepo.jobs.value.filter { it.status == JobStatus.PENDING }
        if (queue.isEmpty()) return
        runJob = viewModelScope.launch {
            cancelRequested = false
            _uiState.update { it.copy(running = true, queueTotal = queue.size, queuePosition = 0) }
            queue.forEachIndexed { index, job ->
                if (cancelRequested) {
                    updateJobStatus(job.id, JobStatus.CANCELLED)
                } else {
                    _uiState.update { it.copy(queuePosition = index + 1) }
                    updateJobStatus(job.id, JobStatus.RUNNING)
                    val ok = execute(job)
                    updateJobStatus(job.id, if (ok) JobStatus.SUCCESS else JobStatus.FAILED)
                    if (!ok && !cancelRequested) {
                        _uiState.update { it.copy(lastError = "任务失败：${job.displayName}") }
                    }
                }
            }
            _uiState.update { it.copy(running = false) }
            runJob = null
        }
    }

    fun cancelRun() {
        cancelRequested = true
        runJob?.cancel()
    }

    /** 把任务产物写回 SAF 目标。 */
    fun writeOutput(job: ImageJob, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    File(job.workPath).inputStream().use { it.copyTo(out) }
                }
                jobsRepo.update(job.copy(outputUri = uri.toString()))
            } catch (_: Exception) {
                _uiState.update { it.copy(lastError = "导出失败：${job.displayName}") }
            }
        }
    }

    /** 清理已完成任务及其工作副本。 */
    fun clearFinished() {
        val finished = jobsRepo.jobs.value.filter { it.status.isFinal() }
        finished.forEach { deleteWorkFile(it) }
        finished.forEach { jobsRepo.remove(it.id) }
        _uiState.update { it.copy(selectedJobId = null) }
    }

    private suspend fun execute(job: ImageJob): Boolean {
        val avb = AvbTool(toolchain)
        val fec = FecTool(toolchain)
        val keyPath = job.config.keyName?.let { keyManager.privateKeyPath(it) }
        val log = StringBuilder()
        val onLine: (String) -> Unit = { line -> log.appendLine(line) }

        var finalJob = job
        val ok = try {
            when (job.config.mode) {
                JobMode.HASH_FOOTER -> {
                    val exit = avb.addHashFooter(
                        AvbTool.HashFooterParams(
                            imagePath = job.workPath,
                            partitionSize = job.config.partitionSize ?: alignToBlock(job.sizeBytes),
                            partitionName = job.config.partitionName ?: ImageDetector.defaultPartitionName(job.partitionType),
                            keyPath = keyPath,
                            algorithm = job.config.algorithm,
                            rollbackIndex = job.config.rollbackIndex,
                            hashAlgorithm = job.config.hashAlgorithm,
                        ),
                        onLine,
                    )
                    log.appendLine("add_hash_footer 退出码: $exit")
                    exit == 0
                }

                JobMode.HASHTREE_FOOTER -> {
                    val exit = avb.addHashtreeFooter(
                        AvbTool.HashtreeFooterParams(
                            imagePath = job.workPath,
                            partitionSize = job.config.partitionSize ?: alignToBlock(job.sizeBytes),
                            partitionName = job.config.partitionName ?: ImageDetector.defaultPartitionName(job.partitionType),
                            keyPath = keyPath,
                            algorithm = job.config.algorithm,
                            rollbackIndex = job.config.rollbackIndex,
                            hashAlgorithm = job.config.hashAlgorithm,
                            fecNumRoots = job.config.fecNumRoots,
                        ),
                        onLine,
                    )
                    log.appendLine("add_hashtree_footer 退出码: $exit")
                    exit == 0
                }

                JobMode.INFO -> {
                    val info = avb.infoImage(job.workPath)
                    if (info.hasFooter) {
                        log.appendLine("算法: ${info.algorithm}")
                        log.appendLine("Rollback Index: ${info.rollbackIndex}")
                        log.appendLine("Flags: ${info.flags}")
                        log.appendLine("descriptors: ${info.descriptors.joinToString()}")
                        log.appendLine("镜像大小: ${info.imageSize}")
                    } else {
                        log.appendLine("未检测到 AVB footer")
                    }
                    true
                }

                JobMode.VERIFY -> {
                    val exit = avb.verifyImage(job.workPath, keyPath, onLine)
                    log.appendLine("verify_image 退出码: $exit")
                    exit == 0
                }

                JobMode.FEC_ENCODE -> {
                    val src = File(job.workPath)
                    val output = File(src.parentFile, "${src.nameWithoutExtension}.fec.img")
                    val exit = fec.encode(src.absolutePath, output.absolutePath, job.config.fecNumRoots ?: 2, onLine)
                    log.appendLine("fec --encode 退出码: $exit")
                    if (exit == 0) {
                        finalJob = job.copy(workPath = output.absolutePath, sizeBytes = output.length())
                    }
                    exit == 0
                }
            }
        } catch (e: Exception) {
            log.appendLine("异常: ${e.message}")
            false
        }
        jobsRepo.update(finalJob.copy(log = log.toString(), finishedAt = System.currentTimeMillis()))
        return ok
    }

    private fun updateJobStatus(jobId: String, status: JobStatus) {
        val job = jobsRepo.jobs.value.find { it.id == jobId } ?: return
        jobsRepo.update(
            job.copy(
                status = status,
                finishedAt = if (status.isFinal()) System.currentTimeMillis() else null,
            )
        )
    }

    private fun deleteWorkFile(job: ImageJob) {
        val workDir = File(app.filesDir, "work").absolutePath
        if (job.workPath.startsWith(workDir)) {
            File(job.workPath).delete()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (_: Exception) {
            null
        }
    }

    private fun alignToBlock(size: Long): Long {
        val block = 4096L
        return if (size % block == 0L) size else (size / block + 1) * block
    }

    private fun JobStatus.isFinal(): Boolean =
        this == JobStatus.SUCCESS || this == JobStatus.FAILED || this == JobStatus.CANCELLED
}
