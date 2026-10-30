package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證日麻的牌面解讀與計算用牌值。 */
class RiichiTileInterpretationPolicyTest {
    /** 赤五的基本牌面就是普通五；計算用牌值只保留赤寶牌特性，不帶外觀。 */
    @Test
    fun `red fives keep only the red dora feature for calculation`() {
        Tile.Suit.entries.forEach { suit ->
            val redFive = RiichiTileValues.redFive(suit)
            val expected = TileValue(Tile.Numeric(suit, 5), ComponentSet.EMPTY.with(RiichiTileComponents.RED_DORA, Unit))

            assertEquals(Tile.Numeric(suit, 5), RiichiTileInterpretationPolicy.canonicalize(redFive.tile))
            assertEquals(expected, RiichiTileInterpretationPolicy.valueOf(redFive))
        }
    }

    /** 從已知牌面的赤寶牌特徵解讀出相同的計算用牌值，張數分組才會一致。 */
    @Test
    fun `red five faces interpret to the same value as the composition`() {
        val face = TileFaceView(
            tile = Tile.Numeric(Tile.Suit.Dot, 5),
            views = ComponentViewSet.EMPTY.with(RiichiTileComponentViews.RED_DORA, Unit),
            appearanceId = RiichiTileValues.RED_FIVE_APPEARANCE_ID,
        )

        assertEquals(RiichiTileInterpretationPolicy.valueOf(RiichiTileValues.redFive(Tile.Suit.Dot)), RiichiTileInterpretationPolicy.valueOf(face))
    }

    /** 共用牌與未知擴充牌不會被日麻 policy 改寫，也不帶赤寶牌特性。 */
    @Test
    fun `unrelated tiles remain unchanged`() {
        val numeric = Tile.Numeric(Tile.Suit.Character, 3)
        val unknown = Tile.Extension(TileTypeId.parse("example:unknown"))

        assertEquals(numeric, RiichiTileInterpretationPolicy.canonicalize(numeric))
        assertEquals(unknown, RiichiTileInterpretationPolicy.canonicalize(unknown))
        assertEquals(TileValue(numeric), RiichiTileInterpretationPolicy.valueOf(TileValue(numeric)))
    }
}
