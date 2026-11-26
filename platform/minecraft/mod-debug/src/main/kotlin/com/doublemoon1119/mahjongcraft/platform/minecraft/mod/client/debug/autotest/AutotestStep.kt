package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

/**
 * 自動測試的一個步驟。
 *
 * - 步驟依序執行。
 * - 某個步驟失敗或拋出例外，不影響之後的步驟。
 *
 * @property id 步驟識別碼，用於報告與只執行部分步驟的選擇。
 */
internal interface AutotestStep {
    val id: String

    /**
     * 執行這個步驟。在用戶端主執行緒上執行，可以暫停等待遊戲進展。
     *
     * @param context 截圖、記錄檢查與等待用的環境。
     */
    suspend fun run(context: AutotestContext)
}
