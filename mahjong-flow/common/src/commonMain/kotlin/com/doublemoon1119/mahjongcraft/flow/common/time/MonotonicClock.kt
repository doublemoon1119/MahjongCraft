package com.doublemoon1119.mahjongcraft.flow.common.time

/**
 * 提供不受系統日期與時區調整影響的遞增時間。
 *
 * 回傳值只允許在同一個 runtime session 內比較，不得直接寫入 persistence 或網路 DTO。
 */
interface MonotonicClock {
    /** 取得目前單調時間軸上的毫秒數。 */
    fun nowMillis(): Long
}
