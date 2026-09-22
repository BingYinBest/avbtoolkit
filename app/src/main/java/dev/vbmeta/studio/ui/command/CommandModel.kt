package dev.vbmeta.studio.ui.command

import androidx.compose.runtime.Immutable

enum class ArgKind { TEXT, NUMBER, FILE, BOOL }

@Immutable
data class CommandArg(
    val key: String,
    val label: String,
    val kind: ArgKind,
    val required: Boolean = false,
    val hint: String = "",
)

@Immutable
data class CommandDef(
    val id: String,
    val title: String,
    val description: String,
    val args: List<CommandArg>,
    /** 官方 avbtool.py 不存在的命令（保留卡片但执行时提示） */
    val unsupported: Boolean = false,
)

val IMAGES_COMMANDS = listOf(
    CommandDef(
        "generate_test_image", "生成测试镜像", "generate_test_image：输出已知模式（0x00..0xff 循环）的测试镜像",
        listOf(
            CommandArg("output", "输出文件", ArgKind.FILE, required = true),
            CommandArg("image_size", "镜像大小", ArgKind.NUMBER, hint = "如 1048576"),
            CommandArg("start_byte", "起始字节", ArgKind.NUMBER),
        ),
    ),
    CommandDef(
        "make_vbmeta_image", "生成 vbmeta", "make_vbmeta_image：构造并签名 vbmeta（flags 0=验证 / 2=禁验）",
        listOf(
            CommandArg("output", "输出文件", ArgKind.FILE, required = true),
            CommandArg("algorithm", "算法", ArgKind.TEXT, hint = "SHA256_RSA4096"),
            CommandArg("key", "私钥", ArgKind.FILE),
            CommandArg("flags", "Flags", ArgKind.NUMBER, hint = "0 或 2"),
            CommandArg("padding_size", "填充字节数", ArgKind.NUMBER),
        ),
    ),
    CommandDef(
        "append_vbmeta_image", "追加 vbmeta", "append_vbmeta_image：把 vbmeta 镜像追加到分区镜像末尾",
        listOf(
            CommandArg("image", "目标镜像", ArgKind.FILE, required = true),
            CommandArg("partition_size", "分区大小", ArgKind.NUMBER, required = true),
            CommandArg("vbmeta_image", "vbmeta 镜像", ArgKind.FILE, required = true),
        ),
    ),
    CommandDef(
        "add_hash_footer", "哈希签名", "add_hash_footer：给 boot/vendor_boot/dtbo 等加 hash footer 并签名",
        listOf(
            CommandArg("image", "镜像", ArgKind.FILE, required = true),
            CommandArg("partition_size", "分区大小", ArgKind.NUMBER, required = true),
            CommandArg("partition_name", "分区名", ArgKind.TEXT, required = true, hint = "boot"),
            CommandArg("key", "私钥", ArgKind.FILE),
            CommandArg("algorithm", "算法", ArgKind.TEXT, hint = "SHA256_RSA4096"),
            CommandArg("rollback_index", "Rollback Index", ArgKind.NUMBER),
            CommandArg("hash_algorithm", "哈希算法", ArgKind.TEXT, hint = "sha256"),
        ),
    ),
    CommandDef(
        "add_hashtree_footer", "哈希树签名", "add_hashtree_footer：给 system/vendor 加 dm-verity 哈希树（含 FEC）",
        listOf(
            CommandArg("image", "镜像", ArgKind.FILE, required = true),
            CommandArg("partition_size", "分区大小", ArgKind.NUMBER, required = true),
            CommandArg("partition_name", "分区名", ArgKind.TEXT, required = true, hint = "system"),
            CommandArg("key", "私钥", ArgKind.FILE),
            CommandArg("algorithm", "算法", ArgKind.TEXT, hint = "SHA256_RSA4096"),
            CommandArg("rollback_index", "Rollback Index", ArgKind.NUMBER),
            CommandArg("hash_algorithm", "哈希算法", ArgKind.TEXT, hint = "sha256"),
            CommandArg("fec_num_roots", "FEC Roots", ArgKind.NUMBER, hint = "2，0=不生成"),
        ),
    ),
    CommandDef(
        "erase_footer", "擦除 footer", "erase_footer：移除镜像上的 AVB footer",
        listOf(
            CommandArg("image", "镜像", ArgKind.FILE, required = true),
            CommandArg("keep_hashtree", "保留哈希树", ArgKind.BOOL),
        ),
    ),
    CommandDef(
        "zero_hashtree", "清零哈希树", "zero_hashtree：清零 hashtree/FEC 数据区（保留 footer）",
        listOf(CommandArg("image", "镜像", ArgKind.FILE, required = true)),
    ),
    CommandDef(
        "extract_vbmeta_image", "提取 vbmeta", "extract_vbmeta_image：从带 footer 的镜像中提取 vbmeta",
        listOf(
            CommandArg("image", "镜像", ArgKind.FILE, required = true),
            CommandArg("output", "输出文件", ArgKind.FILE, required = true),
            CommandArg("padding_size", "填充字节数", ArgKind.NUMBER),
        ),
    ),
    CommandDef(
        "resize_image", "调整大小", "resize_image：调整带 footer 镜像的分区大小",
        listOf(
            CommandArg("image", "镜像", ArgKind.FILE, required = true),
            CommandArg("partition_size", "分区大小", ArgKind.NUMBER, required = true),
        ),
    ),
    CommandDef(
        "set_ab_metadata", "A/B 元数据", "set_ab_metadata：设置 A/B 槽位元数据",
        listOf(
            CommandArg("misc_image", "misc 镜像", ArgKind.FILE, required = true),
            CommandArg("slot_data", "槽位数据", ArgKind.TEXT, required = true, hint = "boot,slot=0"),
        ),
    ),
    CommandDef(
        "update_partition_descriptor", "更新分区描述符", "⚠ 官方 avbtool.py 无此命令；等效流程：提取 vbmeta（extract_vbmeta_image）→ 重建（make_vbmeta_image）→ 追加（append_vbmeta_image）", emptyList(), unsupported = true,
    ),
    CommandDef(
        "resign_image", "重新签名", "resign_image（App 组合实现）：擦除 footer 后用新密钥/算法重新签名（等价 erase_footer + add_*_footer）",
        listOf(
            CommandArg("image", "镜像", ArgKind.FILE, required = true),
            CommandArg("partition_size", "分区大小", ArgKind.NUMBER, required = true),
            CommandArg("partition_name", "分区名", ArgKind.TEXT, required = true, hint = "boot"),
            CommandArg("key", "私钥", ArgKind.FILE, required = true),
            CommandArg("algorithm", "算法", ArgKind.TEXT, hint = "SHA256_RSA4096"),
            CommandArg("hash_algorithm", "哈希算法", ArgKind.TEXT, hint = "sha256"),
            CommandArg("rollback_index", "Rollback Index", ArgKind.NUMBER),
            CommandArg("fec_num_roots", "FEC Roots（哈希树模式）", ArgKind.NUMBER, hint = "2"),
            CommandArg("hashtree", "哈希树模式（dm-verity）", ArgKind.BOOL),
            CommandArg("keep_hashtree", "擦除时保留原哈希树", ArgKind.BOOL),
        ),
    ),
)

val INFO_COMMANDS = listOf(
    CommandDef("version", "工具版本", "version：打印 avbtool 版本", emptyList()),
    CommandDef(
        "check_mldsa_support", "检查 ML-DSA 支持", "检查当前 avbtool 是否支持 ML-DSA（后量子签名）算法（自动检测）", emptyList(),
    ),
    CommandDef(
        "info_image", "查看信息", "info_image：显示镜像 footer/vbmeta 详细信息",
        listOf(CommandArg("image", "镜像", ArgKind.FILE, required = true)),
    ),
    CommandDef(
        "verify_image", "验证镜像", "verify_image：校验签名/hash/hashtree",
        listOf(
            CommandArg("image", "镜像", ArgKind.FILE, required = true),
            CommandArg("key", "公钥或私钥", ArgKind.FILE),
        ),
    ),
    CommandDef(
        "print_partition_digests", "分区摘要", "print_partition_digests：打印分区摘要",
        listOf(CommandArg("image", "镜像", ArgKind.FILE, required = true)),
    ),
    CommandDef(
        "calculate_vbmeta_digest", "vbmeta 摘要", "calculate_vbmeta_digest：计算 vbmeta 摘要",
        listOf(
            CommandArg("image", "镜像", ArgKind.FILE, required = true),
            CommandArg("hash_algorithm", "哈希算法", ArgKind.TEXT, hint = "sha256"),
        ),
    ),
    CommandDef(
        "calculate_kernel_cmdline", "内核命令行", "calculate_kernel_cmdline：生成 dm-verity 内核 cmdline",
        listOf(
            CommandArg("image", "镜像", ArgKind.FILE, required = true),
            CommandArg("hashtree_disabled", "禁用哈希树", ArgKind.BOOL),
        ),
    ),
)

val KEYCERT_COMMANDS = listOf(
    CommandDef(
        "extract_public_key", "提取 AVB 公钥", "extract_public_key：私钥 → AVB 格式公钥（链式签名用）",
        listOf(
            CommandArg("key", "私钥", ArgKind.FILE, required = true),
            CommandArg("output", "输出文件", ArgKind.FILE, required = true),
        ),
    ),
    CommandDef(
        "extract_public_key_digest", "公钥 SHA-256 摘要", "extract_public_key_digest：输出公钥摘要",
        listOf(
            CommandArg("key", "私钥", ArgKind.FILE, required = true),
            CommandArg("output", "输出文件", ArgKind.FILE, required = true),
        ),
    ),
    CommandDef(
        "make_certificate", "创建 ATX 证书", "make_certificate：创建 avb_cert 扩展证书",
        listOf(
            CommandArg("output", "输出文件", ArgKind.FILE, required = true),
            CommandArg("subject", "主题", ArgKind.TEXT, required = true, hint = "AVB key"),
            CommandArg("subject_key", "主题密钥", ArgKind.FILE, required = true),
            CommandArg("authority_key", "授权密钥", ArgKind.FILE),
        ),
    ),
    CommandDef(
        "make_cert_permanent_attributes", "永久属性", "make_cert_permanent_attributes：创建设备永久属性",
        listOf(
            CommandArg("output", "输出文件", ArgKind.FILE, required = true),
            CommandArg("root_authority_key", "根授权密钥", ArgKind.FILE, required = true),
            CommandArg("product_id", "产品 ID（hex）", ArgKind.TEXT),
        ),
    ),
    CommandDef(
        "make_cert_metadata", "证书元数据", "make_cert_metadata：创建证书元数据",
        listOf(
            CommandArg("output", "输出文件", ArgKind.FILE, required = true),
            CommandArg("intermediate_cert", "中间证书", ArgKind.FILE, required = true),
            CommandArg("product_cert", "产品证书", ArgKind.FILE, required = true),
        ),
    ),
    CommandDef(
        "make_cert_unlock_credential", "解锁凭证", "make_cert_unlock_credential：创建解锁凭据",
        listOf(
            CommandArg("output", "输出文件", ArgKind.FILE, required = true),
            CommandArg("intermediate_cert", "中间证书", ArgKind.FILE, required = true),
            CommandArg("unlock_cert", "解锁证书", ArgKind.FILE, required = true),
            CommandArg("challenge", "挑战码", ArgKind.TEXT, required = true),
            CommandArg("unlock_key", "解锁密钥", ArgKind.FILE, required = true),
        ),
    ),
)