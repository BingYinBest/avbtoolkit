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
 * 工具链管理。
 *
 * 三个可执行文件（python3.10/openssl/fec）随 APK 走 jniLibs → nativeLibraryDir：
 * 系统安装时自动赋执行权限（与 .so 同一机制），规避部分 ROM（如 ZUI）上
 * app_data_file 不可执行（File.setExecutable 与 chmod 均无效）的问题。
 *
 * 非可执行内容（python 标准库、avbtool.py 脚本）从 assets 解压到 filesDir，
 * 仅需读取权限。
 */
class ToolchainManager(private val context: Context) {

    private val nativeLibDir: String = context.applicationInfo.nativeLibraryDir
    private val rootDir: File = File(context.filesDir, "toolchain")
    // 命令名别名目录：符号链接 openssl/fec/python3.10 → nativeLibraryDir 下的 .so
    private val aliasDir: File = File(rootDir, "aliases")

    companion object {
        const val PYLIB_ZIP = "toolchain_pylib.zip"
        const val AVBTOOL_ASSET = "avbtool.py"
        const val VERSION_FILE = "VERSION"
        const val ASSET_VERSION = "1"
        private val NATIVES = mapOf(
            "libvbm_python.so" to "python3.10",
            "libvbm_openssl.so" to "openssl",
            "libvbm_fec.so" to "fec",
        )
    }

    /** 当前工具链状态：native 二进制由系统保证，仅检查存在性；标准库/脚本/别名检查解压产物。 */
    fun status(): ToolchainStatus {
        val nativesOk = NATIVES.keys.all { File(nativeLibDir, it).isFile }
        if (!nativesOk) return ToolchainStatus.Missing
        if (!File(rootDir, "python/lib").isDirectory || !File(rootDir, AVBTOOL_ASSET).isFile) {
            return ToolchainStatus.Missing
        }
        val aliasesOk = NATIVES.values.all { File(aliasDir, it).exists() }
        if (!aliasesOk) return ToolchainStatus.Missing
        val versionFile = File(rootDir, VERSION_FILE)
        if (!versionFile.exists()) return ToolchainStatus.Missing
        val actual = versionFile.readText().trim()
        return if (actual == ASSET_VERSION) {
            ToolchainStatus.Ready
        } else {
            ToolchainStatus.VersionMismatch(ASSET_VERSION, actual)
        }
    }

    /** 确保工具链就绪；缺失时从 assets 解压标准库与脚本。native 二进制无需处理。 */
    suspend fun ensureReady(onProgress: (done: Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (status() is ToolchainStatus.Ready) return@withContext true
        val ok = runCatching {
            extract(onProgress)
            true
        }.getOrDefault(false)
        ok && status() is ToolchainStatus.Ready
    }

    /** 清空解压内容（标准库/脚本副本），释放磁盘。native 二进制由系统管理，不可删。 */
    fun clear() {
        rootDir.deleteRecursively()
    }

    fun paths(): ToolchainPaths {
        // 优先走别名（命令名），创建失败时降级直用 native .so（selfCheck 仍可用）
        fun pick(alias: String, native: String): String {
            val a = File(aliasDir, alias)
            return if (a.exists()) a.absolutePath else File(nativeLibDir, native).absolutePath
        }
        return ToolchainPaths(
            python = pick("python3.10", "libvbm_python.so"),
            openssl = pick("openssl", "libvbm_openssl.so"),
            fec = pick("fec", "libvbm_fec.so"),
            avbtool = File(rootDir, AVBTOOL_ASSET).absolutePath,
        )
    }

    /** 进程环境变量：别名目录与 nativeLibraryDir 置入 PATH；PYTHONHOME 指向解压的标准库。 */
    fun env(): Map<String, String> = mapOf(
        "PATH" to "${aliasDir.absolutePath}:$nativeLibDir:${System.getenv("PATH") ?: ""}",
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

        // python 标准库（zip 内部路径 python/lib/python3.10/...）
        val zin = ZipInputStream(context.assets.open(PYLIB_ZIP))
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

        // avbtool.py（纯脚本，由 python 解释执行，无需 exec 权限）
        context.assets.open(AVBTOOL_ASSET).use { input ->
            File(rootDir, AVBTOOL_ASSET).outputStream().use { output -> input.copyTo(output) }
        }

        // 命令名别名（符号链接 → nativeLibraryDir .so），使 avbtool 能找到 openssl/fec
        aliasDir.mkdirs()
        NATIVES.forEach { (so, alias) ->
            runCatching {
                val link = File(aliasDir, alias).toPath()
                java.nio.file.Files.deleteIfExists(link)
                java.nio.file.Files.createSymbolicLink(link, File(nativeLibDir, so).toPath())
            }
        }

        File(rootDir, VERSION_FILE).writeText(ASSET_VERSION)
    }
}