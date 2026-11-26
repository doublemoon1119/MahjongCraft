package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai

/**
 * 一項成立的計台項目及其台數。
 *
 * @property type 成立的項目。
 * @property tai 這一項的台數，例如兩組三元刻算兩台。
 */
data class TaiwanTaiResult(
    val type: TaiwanTaiType,
    val tai: Int,
) {
    init {
        require(tai > 0) { "Tai must be positive" }
    }
}
