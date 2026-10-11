package com.doublemoon1119.mahjongcraft.platform.fabric.server.config

import java.nio.file.Path

/**
 * Fabric server config 的實際位置與可安全顯示的位置。
 *
 * @property path 供檔案 I/O 使用的完整路徑。
 * @property displayPath 不包含主機目錄資訊、可供指令輸出的邏輯路徑。
 */
data class FabricServerConfigLocation(
    val path: Path,
    val displayPath: String,
)
