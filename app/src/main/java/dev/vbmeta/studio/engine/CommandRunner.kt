package dev.vbmeta.studio.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.resume

class CommandException(val exitCode: Int, message: String) : Exception(message)

/**
 * 外部进程执行器。所有 native 工具（python/openssl/fec）都通过这里启动，
 * 避免 shell 拼接，杜绝命令注入。
 */
object CommandRunner {

    /**
     * 执行命令，stdout/stderr 合并后逐行回调。协程取消时会销毁进程。
     * @return 进程退出码
     */
    suspend fun run(
        command: List<String>,
        env: Map<String, String> = emptyMap(),
        onLine: (String) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val process = try {
                val pb = ProcessBuilder(command)
                pb.redirectErrorStream(true)
                env.forEach { (k, v) -> pb.environment()[k] = v }
                pb.start()
            } catch (e: Exception) {
                cont.resumeWith(Result.failure(CommandException(-1, "无法启动进程 ${command.firstOrNull()}: ${e.message}")))
                return@suspendCancellableCoroutine
            }

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val readerThread = Thread {
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        onLine(line)
                    }
                } catch (_: Exception) {
                    // 进程被销毁时流关闭属正常
                }
            }.apply { start() }

            cont.invokeOnCancellation {
                process.destroy()
            }

            val exit = process.waitFor()
            readerThread.join()
            cont.resume(exit)
        }
    }

    /** 执行命令并捕获完整 stdout 文本（适用于输出量小、需要整体解析的场景）。 */
    suspend fun runCapture(
        command: List<String>,
        env: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val sb = StringBuilder()
        val exit = run(command, env) { sb.appendLine(it) }
        return Pair(exit, sb.toString())
    }

    /** 执行命令并捕获完整 stdout 字节（适用于 openssl 输出 DER 等二进制场景）。 */
    suspend fun runCaptureBytes(
        command: List<String>,
        env: Map<String, String> = emptyMap(),
    ): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val process = try {
                val pb = ProcessBuilder(command)
                pb.redirectErrorStream(true)
                env.forEach { (k, v) -> pb.environment()[k] = v }
                pb.start()
            } catch (e: Exception) {
                cont.resumeWith(Result.failure(CommandException(-1, "无法启动进程 ${command.firstOrNull()}: ${e.message}")))
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation { process.destroy() }
            val output = process.inputStream.readBytes()
            val exit = process.waitFor()
            cont.resume(Pair(exit, output))
        }
    }
}
