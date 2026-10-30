package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView

/**
 * 範例中的獨立牌組；每張牌是已公開的牌面，可帶外觀樣式，例如赤五。
 *
 * @property role 牌組語意，不依位置推測和牌張或副露。
 * @property tiles 依排列順序的牌面。
 */
data class RuleCatalogueTileGroup(
    val role: RuleCatalogueTileGroupRole,
    val tiles: List<TileFaceView>,
) {
    init {
        require(tiles.isNotEmpty()) { "Catalogue tile group must not be empty" }
        require(role != RuleCatalogueTileGroupRole.WINNING_TILE || tiles.size == 1) { "Winning tile group must contain exactly one tile" }
    }
}
