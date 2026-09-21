# AVB 概念速览

## vbmeta

vbmeta 是 AVB（Android Verified Boot）的"总签名"。它包含一系列 **descriptor**（描述各分区内容的摘要）和一条对全部数据的 RSA 签名。bootloader 启动时校验 vbmeta 签名，再按 descriptor 校验对应分区。

- `flags=0`：强制验证所有描述的分区
- `flags=2`（FLAG_VERIFICATION_DISABLED）：跳过验证（即"禁验 vbmeta"）

## descriptor 类型

- **Hash descriptor**：记录单个分区（如 boot）的哈希摘要，分区内容改变即校验失败
- **Hashtree descriptor**：记录文件系统分区的哈希树根（dm-verity），支持按块校验与纠错
- **Chain Partition descriptor**：把某个分区（如 boot）的验证"链式"委托给该分区自身携带的公钥（常用于 ODM/vendor 独立签名）
- **Property descriptor**：携带厂商自定义属性
- **Kernel Cmdline descriptor**：注入内核命令行

## dm-verity 与 FEC

- **dm-verity** 是内核块层校验机制：将 system 等分区按 4K 块建立哈希树，读取时逐块校验，防止篡改与静默损坏。
- **FEC（Forward Error Correction）**：用 Reed-Solomon 纠错码为数据块生成冗余，损坏少量块时可自动修复而不触发校验失败。avbtool 的 `add_hashtree_footer` 默认生成 FEC（roots=2）。

## footer

镜像末尾有一个 64 字节的 AVB footer（magic `AVBf`），记录原始镜像大小与 vbmeta 在分区内的偏移，是 bootloader 找到 vbmeta 的入口。

## 常用命令对照（工坊模式）

| 工坊模式 | avbtool 命令 | 用途 |
|---|---|---|
| 哈希签名 | add_hash_footer | boot/vendor_boot/init_boot/dtbo |
| 哈希树签名 | add_hashtree_footer | system/vendor/product（dm-verity + FEC） |
| 查看信息 | info_image | 查看 footer/vbmeta 内容 |
| 验证 | verify_image | 校验签名与内容一致性 |
| FEC 编码 | fec --encode | 单独生成 FEC 数据 |

## 提示

- 解锁 bootloader 后仍需正确签名才能通过 bootloader 校验；不签名或签名错误会进 bootloader 提示 "AVB verification failed"。
- 私钥即信任根：用自签密钥的镜像只能在该密钥体系下通过验证。
