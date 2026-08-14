package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證日麻擴充牌的正規牌面與赤寶牌身分。 */
class RiichiTileInterpretationPolicyTest {
    /** 驗證三種赤五分別等同同花色的普通五。 */
    @Test
    fun `red fives canonicalize to regular fives`() {
        Tile.Suit.entries.forEach { suit ->
            val redFive = RiichiTileTypes.redFive(suit)
            val legacyRedFive = Tile.Numeric(suit, 5, isRed = true)

            assertEquals(Tile.Numeric(suit, 5), RiichiTileInterpretationPolicy.canonicalize(redFive))
            assertEquals(Tile.Numeric(suit, 5), RiichiTileInterpretationPolicy.canonicalize(legacyRedFive))
            assertTrue(RiichiTileInterpretationPolicy.isRedDora(redFive))
            assertTrue(RiichiTileInterpretationPolicy.isRedDora(legacyRedFive))
        }
    }

    /** 驗證共用牌與未知擴充牌不會被日麻 policy 改寫或誤判為赤寶牌。 */
    @Test
    fun `unrelated tiles remain unchanged`() {
        val numeric = Tile.Numeric(Tile.Suit.Character, 3)
        val unknown = Tile.Extension(TileTypeId.parse("example:unknown"))

        assertEquals(numeric, RiichiTileInterpretationPolicy.canonicalize(numeric))
        assertEquals(unknown, RiichiTileInterpretationPolicy.canonicalize(unknown))
        assertFalse(RiichiTileInterpretationPolicy.isRedDora(numeric))
        assertFalse(RiichiTileInterpretationPolicy.isRedDora(unknown))
    }
}
