package com.doublemoon1119.mahjongcraft.logic.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileValue

/**
 * 規則牌組中一種牌值的張數，不包含牌張身分或洗牌順序。
 *
 * @property value 基本牌面及其特性。
 * @property count 使用的張數，必須大於零。
 */
data class TileCompositionEntry(
    val value: TileValue,
    val count: Int,
) {
    init {
        require(count > 0) { "Tile composition count must be positive" }
    }
}
