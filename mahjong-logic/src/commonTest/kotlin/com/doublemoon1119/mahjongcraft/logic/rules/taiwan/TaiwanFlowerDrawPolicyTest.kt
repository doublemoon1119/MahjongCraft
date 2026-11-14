package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpContext
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpDecision
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** [TaiwanFlowerDrawPolicy] 的補花判定測試。 */
class TaiwanFlowerDrawPolicyTest {
    /** 一般牌不需要自動處理。 */
    @Test
    fun `ordinary tile keeps the player turn`() {
        val tile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val state = FakeTableStateFactory.create(tileWall = TileWall(listOf(tile)))

        assertEquals(DrawFollowUpDecision.None, TaiwanFlowerDrawPolicy.resolve(context(state, tile)))
    }

    /** 花牌尚有補牌來源時自動公開。 */
    @Test
    fun `flower is set aside while a replacement remains`() {
        val flower = FakeIdentifiedTileFactory.create(Tile.Extension(TaiwanTileTypes.SPRING))
        val replacement = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val state = FakeTableStateFactory.create(tileWall = TileWall(listOf(replacement)))

        assertEquals(
            expected = DrawFollowUpDecision.SetAsideThenDecide(TAIWAN_REVEAL_FLOWER_ACTION),
            actual = TaiwanFlowerDrawPolicy.resolve(context(state, flower)),
        )
    }

    /** 花牌沒有合法補牌來源時以明確流局結束。 */
    @Test
    fun `flower ends the round when no replacement remains`() {
        val flower = FakeIdentifiedTileFactory.create(Tile.Extension(TaiwanTileTypes.SPRING))
        val state = FakeTableStateFactory.create(tileWall = TileWall(emptyList()))

        assertEquals(
            expected = DrawFollowUpDecision.EndRound(TaiwanExhaustiveDrawReason.NoSupplementalTile),
            actual = TaiwanFlowerDrawPolicy.resolve(context(state, flower)),
        )
    }

    /** 建立指定牌張的摸牌後續判定資料。 */
    private fun context(
        state: TableState,
        tile: IdentifiedTile,
    ): DrawFollowUpContext = DrawFollowUpContext(
        tableState = state,
        playerId = state.currentPlayer.id,
        drawnTile = tile,
        source = DrawSource.LiveWall,
        completedStepCount = 0,
    )
}
