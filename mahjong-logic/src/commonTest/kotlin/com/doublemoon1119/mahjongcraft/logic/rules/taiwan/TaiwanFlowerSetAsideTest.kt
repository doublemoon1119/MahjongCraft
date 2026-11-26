package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 台灣麻將公開花牌的狀態變化測試。 */
class TaiwanFlowerSetAsideTest {
    /** 公開花牌只移動指定牌張並保留其他手牌。 */
    @Test
    fun `flower moves from drawn slot to public flower area`() {
        val flower = FakeIdentifiedTileFactory.create(Tile.Extension(TaiwanTileTypes.SPRING))
        val ordinary = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val state = FakeTableStateFactory.create(turnPhase = TurnPhase.AwaitingAction)
        val player = state.currentPlayer.copy(hand = Hand(tiles = listOf(ordinary), lastDrawn = flower))
        val table = state.copy(players = state.players.map { if (it.id == player.id) player else it })
        val module = TaiwanRuleModule("mahjongcraft:taiwan", TaiwanRuleConfig())

        val updated = assertNotNull(module.applyTileSetAsideAction(table, player.id, TAIWAN_REVEAL_FLOWER_ACTION))
        val updatedPlayer = updated.players.first { it.id == player.id }
        assertEquals(listOf(ordinary), updatedPlayer.hand.tiles)
        assertNull(updatedPlayer.hand.lastDrawn)
        assertEquals(listOf(flower), updatedPlayer.tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles)
    }

    /** 非花牌不能套用補花動作。 */
    @Test
    fun `ordinary tile cannot be set aside as a flower`() {
        val ordinary = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val state = FakeTableStateFactory.create(turnPhase = TurnPhase.AwaitingAction)
        val player = state.currentPlayer.copy(hand = Hand(lastDrawn = ordinary))
        val table = state.copy(players = state.players.map { if (it.id == player.id) player else it })
        val module = TaiwanRuleModule("mahjongcraft:taiwan", TaiwanRuleConfig())

        assertNull(module.applyTileSetAsideAction(table, player.id, TAIWAN_REVEAL_FLOWER_ACTION))
    }
}
