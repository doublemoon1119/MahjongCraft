package com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RiichiFuuro] 明暗判定之單元測試。
 */
class RiichiFuuroTest {

    /**
     * 測試只有暗槓是暗面子，其他副露都是明面子。
     */
    @Test
    fun `test only ankan is concealed`() {
        val tile = Tile.Numeric(Tile.Suit.Character, 3)

        assertFalse(RiichiFuuro(RiichiMentsu.Ankan(tile), from = RelativeDirection.Across).isOpen)
        assertTrue(RiichiFuuro(RiichiMentsu.Kotsu(tile), from = RelativeDirection.Left).isOpen)
        assertTrue(RiichiFuuro(RiichiMentsu.Shuntsu(Tile.Numeric(Tile.Suit.Character, 1)), from = RelativeDirection.Left).isOpen)
        assertTrue(RiichiFuuro(RiichiMentsu.Minkan(tile), from = RelativeDirection.Right).isOpen)
        assertTrue(RiichiFuuro(RiichiMentsu.Kakan(tile), from = RelativeDirection.Across).isOpen)
    }
}
