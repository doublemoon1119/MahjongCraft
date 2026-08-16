package com.doublemoon1119.mahjongcraft.flow.common.time

/**
 * 提供不受系統日期與時區調整影響的遞增時間。
 *
 * 回傳值只允許在同一個 runtime session 內比較，不得直接寫入 persistence 或網路 DTO。
 *
 * **必須反映「遊戲實際運行的時間」，不能是純粹的系統時間**：實作若採用真實時鐘（例如
 * `System.nanoTime()`），會在遊戲暫停（例如 Minecraft 單機版 ESC 選單暫停伺服器 tick）時繼續前進，
 * 導致玩家決策計時器誤判暫停期間也算「思考時間」，恢復後立刻被判定逾時、強制自動操作——這是曾經
 * 在這裡踩過的真實問題，不是假設。平台層實作應該用該平台實際「運行中」的時間單位（例如 Minecraft
 * 的 server tick 計數）作為來源，暫停時自然停止前進。
 */
interface MonotonicClock {
    /** 取得目前單調時間軸上的毫秒數。 */
    fun nowMillis(): Long
}
