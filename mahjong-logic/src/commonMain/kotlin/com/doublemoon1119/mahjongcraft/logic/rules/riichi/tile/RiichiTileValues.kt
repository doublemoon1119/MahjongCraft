package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 日麻牌組使用的特殊牌值。 */
object RiichiTileValues {
    /** 赤五的外觀樣式；只決定外觀，計分只讀 [RiichiTileComponents.RED_DORA]。 */
    val RED_FIVE_APPEARANCE_ID: String = MahjongCraftMetadata.id("riichi/red_five")

    /**
     * 建立指定花色的赤五：基本牌面是普通五，另帶赤寶牌特性與赤五外觀。
     *
     * @param suit 赤五的花色。
     * @return 赤五牌值。
     */
    fun redFive(suit: Tile.Suit): TileValue = TileValue(
        tile = Tile.Numeric(suit, 5),
        components = ComponentSet.EMPTY
            .with(RiichiTileComponents.RED_DORA, Unit)
            .with(BuiltInTileComponents.APPEARANCE, RED_FIVE_APPEARANCE_ID),
    )

    /** 使用透明牌時，每種牌四張中透明的張數；最後一張保持不透明。 */
    const val TRANSPARENT_COPIES: Int = 3

    /**
     * 使用透明牌時，為同一種牌的第 [copyIndex] 張加上透明牌組件。
     *
     * @param value 原本的牌值。
     * @param copyIndex 這張牌在同一種牌四張中的順序，從 0 開始；洗牌前的順序，不影響牌山位置。
     * @param transparentTiles 規則是否使用透明牌。
     * @return 前 [TRANSPARENT_COPIES] 張在使用透明牌時帶透明牌組件，其他維持原牌值。
     */
    fun withTransparency(value: TileValue, copyIndex: Int, transparentTiles: Boolean): TileValue = if (transparentTiles && copyIndex < TRANSPARENT_COPIES) {
        value.copy(components = value.components.with(BuiltInTileComponents.TRANSPARENT, Unit))
    } else {
        value
    }
}
