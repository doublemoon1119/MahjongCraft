package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy

/**
 * 擴充失敗判斷：除了一般例外，也隔離 JVM 的類別連結錯誤（[LinkageError]）。
 *
 * 連結錯誤代表擴充與目前安裝的類別對不上，例如擴充以不同版本的 MahjongCraft 編譯，或缺少它依賴的其他 mod；
 * 只有該擴充的功能無法使用，不代表執行環境已不可靠。記憶體不足、堆疊溢位等其他錯誤仍不隔離。
 * client 與 server graph 共用同一個實例，由各載入器的共用 DI 模組提供。
 */
class LinkageIsolatingExtensionFailurePolicy : ExtensionFailurePolicy {
    override fun isIsolatable(failure: Throwable): Boolean = failure is LinkageError || ExtensionFailurePolicy.Default.isIsolatable(failure)
}
