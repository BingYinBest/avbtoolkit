package dev.vbmeta.studio.engine

/**
 * fec（dm-verity 前向纠错）工具封装。
 * avbtool add_hashtree_footer 内部会调用 fec，这里的独立入口用于
 * 对已有 footer 的镜像单独编码/查询 FEC 数据。
 */
class FecTool(private val toolchain: ToolchainManager) {

    /** fec --encode --roots N <input> <output>，output 为带 FEC 数据的新镜像。 */
    suspend fun encode(
        input: String,
        output: String,
        roots: Int = 2,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = listOf(
            toolchain.paths().fec,
            "--encode",
            "--roots", roots.toString(),
            input,
            output,
        )
        return CommandRunner.run(cmd, toolchain.env(), onLine)
    }

    /** fec --print-fec-size <dataSize> --roots N，返回估算的 FEC 数据大小（字节）。 */
    suspend fun fecSize(
        dataSize: Long,
        roots: Int = 2,
    ): Long? {
        val (exit, out) = CommandRunner.runCapture(
            listOf(
                toolchain.paths().fec,
                "--print-fec-size", dataSize.toString(),
                "--roots", roots.toString(),
            ),
            toolchain.env(),
        )
        if (exit != 0) return null
        return out.trim().toLongOrNull()
    }
}
