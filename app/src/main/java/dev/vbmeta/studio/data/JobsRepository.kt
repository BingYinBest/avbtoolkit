package dev.vbmeta.studio.data

import android.content.Context
import dev.vbmeta.studio.model.ImageJob
import dev.vbmeta.studio.model.JobMode
import dev.vbmeta.studio.model.JobStatus
import dev.vbmeta.studio.model.PartitionType
import dev.vbmeta.studio.model.SignConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 镜像任务仓库：内存 StateFlow + filesDir/jobs.json 持久化。
 */
class JobsRepository(context: Context) {

    private val file: File = File(context.filesDir, "jobs.json")

    private val _jobs = MutableStateFlow(load())
    val jobs: StateFlow<List<ImageJob>> = _jobs.asStateFlow()

    fun add(job: ImageJob) {
        _jobs.value = _jobs.value + job
        persist()
    }

    fun update(job: ImageJob) {
        _jobs.value = _jobs.value.map { if (it.id == job.id) job else it }
        persist()
    }

    fun remove(id: String) {
        _jobs.value = _jobs.value.filterNot { it.id == id }
        persist()
    }

    fun clear() {
        _jobs.value = emptyList()
        persist()
    }

    private fun persist() {
        try {
            val arr = JSONArray()
            _jobs.value.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString())
        } catch (_: Exception) {
            // 持久化失败不阻断内存状态
        }
    }

    private fun load(): List<ImageJob> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val job = arr.optJSONObject(i)?.toImageJob() ?: continue
                    add(job)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun ImageJob.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("displayName", displayName)
        .put("srcUri", srcUri)
        .put("workPath", workPath)
        .put("partitionType", partitionType.name)
        .put("sizeBytes", sizeBytes)
        .put("config", config.toJson())
        .put("status", status.name)
        .put("log", log)
        .put("createdAt", createdAt)
        .put("finishedAt", finishedAt)
        .put("outputUri", outputUri)

    private fun SignConfig.toJson(): JSONObject = JSONObject()
        .put("mode", mode.name)
        .put("keyName", keyName)
        .put("algorithm", algorithm)
        .put("rollbackIndex", rollbackIndex)
        .put("hashAlgorithm", hashAlgorithm)
        .put("fecNumRoots", fecNumRoots)
        .put("vbmetaFlags", vbmetaFlags)
        .put("partitionSize", partitionSize)
        .put("partitionName", partitionName)
        .put("extraImagePath", extraImagePath)
        .put("keepHashtree", keepHashtree)
        .put("paddingSize", paddingSize)

    private fun JSONObject.toImageJob(): ImageJob = ImageJob(
        id = optString("id"),
        displayName = optString("displayName"),
        srcUri = optString("srcUri"),
        workPath = optString("workPath"),
        partitionType = runCatching { PartitionType.valueOf(optString("partitionType")) }.getOrDefault(PartitionType.OTHER),
        sizeBytes = optLong("sizeBytes"),
        config = optJSONObject("config")?.toSignConfig() ?: SignConfig(),
        status = runCatching { JobStatus.valueOf(optString("status")) }.getOrDefault(JobStatus.PENDING),
        log = optString("log"),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        finishedAt = if (isNull("finishedAt")) null else optLong("finishedAt"),
        outputUri = if (isNull("outputUri")) null else optString("outputUri"),
    )

    private fun JSONObject.toSignConfig(): SignConfig = SignConfig(
        mode = runCatching { JobMode.valueOf(optString("mode")) }.getOrDefault(JobMode.HASH_FOOTER),
        keyName = if (isNull("keyName")) null else optString("keyName"),
        algorithm = optString("algorithm", "SHA256_RSA4096"),
        rollbackIndex = optLong("rollbackIndex"),
        hashAlgorithm = optString("hashAlgorithm", "sha256"),
        fecNumRoots = if (isNull("fecNumRoots")) null else optInt("fecNumRoots"),
        vbmetaFlags = optInt("vbmetaFlags"),
        partitionSize = if (isNull("partitionSize")) null else optLong("partitionSize"),
        partitionName = if (isNull("partitionName")) null else optString("partitionName"),
        extraImagePath = if (isNull("extraImagePath")) null else optString("extraImagePath"),
        keepHashtree = optBoolean("keepHashtree"),
        paddingSize = if (isNull("paddingSize")) null else optInt("paddingSize"),
    )
}
