package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import kotlinx.serialization.json.JsonElement

/**
 * 歷史公開資料的窄責任解碼器。
 *
 * @param T 轉換後的歷史讀模型型別。
 */
fun interface HistoryReplayProjectionCodec<T> {
    /**
     * 解碼公開資料，不回傳原始私有 JSON。
     *
     * @param value 解碼邊界內部的投影。
     * @param context 經驗證的索引及工作預算。
     * @return 歷史讀模型。
     */
    fun decode(value: JsonElement, context: HistoryReplayProjectionContext): T
}
