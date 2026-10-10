package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/**
 * 用於延續 keyset 分頁的排序值。
 *
 * @property numericValue 非 null 排序值。
 * @property nullBucket null 排序值所在的排序桶。
 */
data class HistorySortValue(
    val numericValue: Long?,
    val nullBucket: Boolean,
) {
    init {
        require(nullBucket == (numericValue == null)) { "History cursor null bucket does not match sort value" }
    }
}
