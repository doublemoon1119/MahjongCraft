package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue
import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 範例中的獨立牌組，沿用數牌、字牌及帶有穩定 ID 的擴充牌模型。
 *
 * @property role 牌組語意，不依位置推測和牌張或副露。
 * @property tiles 牌種排列順序。
 */
data class RuleCatalogueTileGroup(
    val role: RuleCatalogueTileGroupRole,
    val tiles: List<Tile>,
) {
    init {
        require(tiles.isNotEmpty()) { "Catalogue tile group must not be empty" }
        require(role != RuleCatalogueTileGroupRole.WINNING_TILE || tiles.size == 1) { "Winning tile group must contain exactly one tile" }
    }
}
