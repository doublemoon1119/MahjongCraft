package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 規則選擇結果的呈現差異測試，包含手牌與牌牆之間的牌張重新分配。 */
class RuleDecisionPresentationUpdateTest {
    /** 標準日麻模組提供牌序，測試不進行計分或和牌判定。 */
    private val module = RiichiRuleModule("test:rule_decision", RiichiRuleConfig())

    /** 桌況完全相同時，不發布多餘的牌區、規則或局況更新。 */
    @Test
    fun `unchanged table does not publish presentation updates`() {
        val table = FakeTableStateFactory.create(config = module.config)
        val publisher = FakeGamePresentationPublisher()

        RuleDecisionPresentationUpdate(table, table).publish(publisher, module)

        assertTrue(publisher.getPublishedPlayerTilesUpdates(table.id).isEmpty())
        assertTrue(publisher.getPublishedWallLayoutUpdates(table.id).isEmpty())
        assertEquals(0, publisher.getRuleStateUpdateCount(table.id))
        assertNull(publisher.getPublishedRoundInfo(table.id))
    }

    /** 將手牌換回牌牆時，同步新牌牆位置及玩家最終手牌，不重建牌牆。 */
    @Test
    fun `exchanging hand and wall tiles publishes final areas without an opening`() {
        val handTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val wallTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val slot = TileWallPlacement(TileWallPosition(side = 0, stack = 0, layer = 0))
        val previous = FakeTableStateFactory.create(
            config = module.config,
            players = listOf(FakeMahjongPlayerFactory.create(hand = Hand(tiles = listOf(handTile)))),
            tileWall = TileWall(listOf(wallTile)),
            physicalWallLayout = TileWallPhysicalLayout(mapOf(wallTile.id to slot)),
        )
        val current = previous.copy(
            players = listOf(previous.players.single().copy(hand = Hand(tiles = listOf(wallTile)))),
            tileWall = TileWall(listOf(handTile)),
            physicalWallLayout = TileWallPhysicalLayout(mapOf(handTile.id to slot)),
        )
        val publisher = FakeGamePresentationPublisher()

        RuleDecisionPresentationUpdate(previous, current).publish(publisher, module)

        assertEquals(listOf(current.physicalWallLayout), publisher.getPublishedWallLayoutUpdates(current.id))
        assertEquals(listOf(wallTile.id), publisher.getPublishedPlayerTiles(current.id, 0)?.standingTileIds)
        assertEquals(current, publisher.getPublishedRoundInfo(current.id))
        assertNull(publisher.getPublishedWallStructure(current.id))
        assertNull(publisher.getPublishedInitialDeal(current.id))
    }

    /** 移出牌與補入手牌的牌使用同一份最終狀態，剩餘牌牆仍保有自己的布局。 */
    @Test
    fun `set aside tiles and replacements update together`() {
        val threePlayerModule = ThreePlayerRiichiRuleModule("test:three_rule_decision", ThreePlayerRiichiRuleConfig())
        val north = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val replacement = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val remaining = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val firstSlot = TileWallPlacement(TileWallPosition(side = 0, stack = 0, layer = 0))
        val secondSlot = TileWallPlacement(TileWallPosition(side = 0, stack = 0, layer = 1))
        val previous = FakeTableStateFactory.create(
            config = threePlayerModule.config,
            players = listOf(FakeMahjongPlayerFactory.create(hand = Hand(tiles = listOf(north)))),
            tileWall = TileWall(listOf(replacement, remaining)),
            physicalWallLayout = TileWallPhysicalLayout(mapOf(replacement.id to firstSlot, remaining.id to secondSlot)),
        )
        val current = previous.copy(
            players = listOf(
                previous.players.single().copy(
                    hand = Hand(tiles = listOf(replacement)),
                    playerRuleState = RiichiPlayerState(nukiDoraTiles = listOf(north)),
                ),
            ),
            tileWall = TileWall(listOf(remaining)),
            physicalWallLayout = TileWallPhysicalLayout(mapOf(remaining.id to secondSlot)),
        )
        val publisher = FakeGamePresentationPublisher()

        RuleDecisionPresentationUpdate(previous, current).publish(publisher, threePlayerModule)

        val update = publisher.getPublishedPlayerTiles(current.id, 0)
        assertEquals(listOf(replacement.id), update?.standingTileIds)
        assertEquals(listOf(north.id), update?.setAsideTileIds)
        assertEquals(listOf(current.physicalWallLayout), publisher.getPublishedWallLayoutUpdates(current.id))
    }

    /** 僅改變移出區而未改動手牌時，仍須更新該玩家的牌區。 */
    @Test
    fun `set aside change alone refreshes the player area`() {
        val threePlayerModule = ThreePlayerRiichiRuleModule("test:three_rule_decision", ThreePlayerRiichiRuleConfig())
        val north = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val previous = FakeTableStateFactory.create(config = threePlayerModule.config)
        val current = previous.copy(
            players = previous.players.mapIndexed { index, player ->
                if (index == 0) player.copy(playerRuleState = RiichiPlayerState(nukiDoraTiles = listOf(north))) else player
            },
        )
        val publisher = FakeGamePresentationPublisher()

        RuleDecisionPresentationUpdate(previous, current).publish(publisher, threePlayerModule)

        assertEquals(listOf(0), publisher.getPublishedPlayerTilesUpdates(current.id).map { it.seatIndex })
        assertEquals(listOf(north.id), publisher.getPublishedPlayerTiles(current.id, 0)?.setAsideTileIds)
    }
}
