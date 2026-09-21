package dev.vbmeta.studio.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class ToolchainPaths(
    val python: String,
    val openssl: String,
    val fec: String,
    val avbtool: String,
)

sealed class ToolchainStatus {
    data object Ready : ToolchainStatus()
    data object Missing : ToolchainStatus()
    data class VersionMismatch(val expected: String, val actual: String) : ToolchainStatus()
}

/**
 * 管理内嵌工具链（assets/toolchain.zip → filesDir/toolchain）。
 * 工具链包含：musl 静态 python、静态 openssl、fec、avbtool.py。
 */
class ToolchainManager(private val context: Context) {

    private val rootDir: File = File(context.filesDir, "toolchain")
    private val binDir: File = File(rootDir, "bin")
    private val pythonBin: File = File(rootDir, "python/bin")

    companion object {
        const val ASSET_ZIP = "toolchain.zip"
        const val VERSION_FILE = "VERSION"
        /** 与 CI 打包的工具链内容对应，升级工具链时需同步 +1。 */
        const val ASSET_VERSION = "1"
        private const val EXECUTABLES = "bin/openssl,bin/fec,bin/avbtool.py,python/bin/python3.10"
    }

    /** 当前工具链状态：文件齐备且版本匹配才算 Ready。 */
    fun status(): ToolchainStatus {
        val expected = EXECUTABLES.split(",")
        val allPresent = expected.all {
            val f = File(rootDir, it)
            f.isFile && f.canExecute()
        }
        if (!allPresent) return ToolchainStatus.Missing
        val versionFile = File(rootDir, VERSION_FILE)
        if (!versionFile.exists()) return ToolchainStatus.Missing
        val actual = versionFile.readText().trim()
        return if (actual == ASSET_VERSION) {
            ToolchainStatus.Ready
        } else {
            ToolchainStatus.VersionMismatch(ASSET_VERSION, actual)
        }
    }

    /** 确保工具链就绪；未就绪则从 assets 解压。返回是否就绪。 */
    suspend fun ensureReady(onProgress: (done: Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (status() is ToolchainStatus.Ready) return@withContext true
        val ok = runCatching {
            extract(onProgress)
            true
        }.getOrDefault(false)
        ok && status() is ToolchainStatus.Ready
    }

    /** 清空已解压的工具链，释放磁盘空间。 */
    fun clear() {
        rootDir.deleteRecursively()
    }

    fun paths(): ToolchainPaths = ToolchainPaths(
        python = File(pythonBin, "python3.10").absolutePath,
        openssl = File(binDir, "openssl").absolutePath,
        fec = File(binDir, "fec").absolutePath,
        avbtool = File(binDir, "avbtool.py").absolutePath,
    )

    /** 进程环境变量：把工具链 bin 目录置于 PATH 前，使 avbtool 能找到 openssl/fec。 */
    fun env(): Map<String, String> = mapOf(
        "PATH" to "${binDir.absolutePath}:${pythonBin.absolutePath}:${System.getenv("PATH") ?: ""}",
        "PYTHONHOME" to File(rootDir, "python").absolutePath,
    )

    /** 运行各组件 --version，返回组件名 → 版本信息（供首页展示与自检）。 */
    suspend fun selfCheck(): Map<String, String> = withContext(Dispatchers.IO) {
        val env = env()
        suspend fun probe(command: List<String>): String {
            return try {
                val (exit, out) = CommandRunner.runCapture(command, env)
                if (exit != 0) "不可用" else out.trim().lineSequence().firstOrNull() ?: ""
            } catch (_: Exception) {
                "不可用"
            }
        }
        mapOf(
            "Python" to probe(listOf(paths().python, "--version")),
            "OpenSSL" to probe(listOf(paths().openssl, "version")),
            "fec" to probe(listOf(paths().fec, "--help")).ifEmpty { "可用" },
            "avbtool" to probe(listOf(paths().python, paths().avbtool, "version")),
        )
    }

    private fun extract(onProgress: (done: Int) -> Unit) {
        rootDir.deleteRecursively()
        rootDir.mkdirs()

        val zin = ZipInputStream(context.assets.open(ASSET_ZIP))
        var count = 0
        try {
            var entry = zin.nextEntry
            while (entry != null) {
                val target = File(rootDir, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out -> zin.copyTo(out) }
                    count++
                    onProgress(count)
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        } finally {
            zin.close()
        }

        listOf(
            File(pythonBin, "python3.10"),
            File(binDir, "openssl"),
            File(binDir, "fec"),
            File(binDir, "avbtool.py"),
        ).forEach { makeExecutable(it) }

        File(rootDir, VERSION_FILE).writeText(ASSET_VERSION)
    }

    /**
     * 确保文件可执行。File.setExecutable 在部分 ROM（如 ZUI）上不生效，
     * 需要 fallback 到系统 chmod；仍失败则交给调用方处理。
     */
    private fun makeExecutable(file: File) {
        if (file.setExecutable(true, false) && file.canExecute()) return
        runCatching {
            ProcessBuilder("/system/bin/chmod", "755", file.absolutePath).start().waitFor()
        }
    }
}
