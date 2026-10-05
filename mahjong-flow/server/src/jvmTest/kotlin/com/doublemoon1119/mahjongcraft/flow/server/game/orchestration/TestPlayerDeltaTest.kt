package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 測試用玩家增量的清空、列表變動、牌河與副露邊界。 */
class TestPlayerDeltaTest {
    /** 驗證 nullable 欄位的清空與未變更可明確區分。 */
    @Test
    fun `clearing nullable fields is different from no change`() {
        val drawn = tile()
        val before = player().copy(
            hand = Hand(lastDrawn = drawn),
            playerRuleState = RiichiPlayerState(riichiTile = drawn),
        )
        val after = before.copy(
            hand = Hand(),
            playerRuleState = null,
        )
        val delta = requireNotNull(TestPlayerDelta.between(before, after))
        assertNull(delta.lastDrawn?.value)
        assertNull(delta.ruleState?.value)
        assertEquals(after, delta.applyTo(before))
    }

    /** 驗證副露及牌河差異保留牌的身分與排列順序。 */
    @Test
    fun `meld and discard changes preserve tile identity and order`() {
        val first = tile()
        val second = tile()
        val third = tile()
        val claimed = tile()
        val before = player().copy(
            hand = Hand(tiles = listOf(first, second, third)),
            discardPile = RiichiDiscardPile(listOf(RiichiDiscardEntry(claimed, isRiichi = true))),
        )
        val meld = Meld(MeldType.PON, listOf(first, second, claimed), claimed, RelativeDirection.Left)
        val after = before.copy(
            hand = Hand(tiles = listOf(third), melds = listOf(meld)),
            discardPile = before.discardPile.takeLast(),
            actionHistory = listOf(GameAction.Pon(claimed.id, listOf(first.id, second.id))),
        )
        val delta = requireNotNull(TestPlayerDelta.between(before, after))
        assertIs<TestDiscardDelta.Riichi>(delta.discards)
        assertEquals(after, delta.applyTo(before))
        assertEquals(listOf(third), delta.applyTo(before).hand.tiles)
        assertEquals(listOf(meld), delta.applyTo(before).hand.melds)
    }

    /** 驗證狀態完全相同時不建立差異。 */
    @Test
    fun `unchanged player has no delta`() {
        val before = player()
        assertNull(TestPlayerDelta.between(before, before))
    }

    /** 建立具穩定初始座位的測試玩家。 */
    private fun player(): MahjongPlayer = MahjongPlayer(
        id = Uuid.random(),
        initialSeatIndex = 0,
        discardPile = RiichiDiscardPile(),
        seatWind = Wind.EAST,
    )

    /** 建立具有獨立身分的測試牌。 */
    private fun tile(): IdentifiedTile = IdentifiedTile(Uuid.random(), Tile.Honor.East)
}
