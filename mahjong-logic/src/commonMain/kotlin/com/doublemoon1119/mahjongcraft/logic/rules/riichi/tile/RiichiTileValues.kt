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
}
