package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

/**
 * 一項成立的台及其計數。
 *
 * @property type 成立的項目。
 * @property count 成立次數，例如正花張數或三元刻組數。
 */
data class TaiwanTaiResult(val type: TaiwanTaiType, val count: Int = 1) {
    init {
        require(count in 1..Int.MAX_VALUE / type.tai) { "Tai count must be positive and fit the supported range" }
    }

    /** 這一項的總台數。 */
    val totalTai: Int get() = type.tai * count
}
