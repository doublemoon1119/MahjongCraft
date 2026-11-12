package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreas
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 八仙過海與七搶一的資格、花牌歸屬及付款測試。 */
class TaiwanSpecialFlowerWinTest {
    /** 本人集齊八張花時成立八仙過海，由其餘三家付款。 */
    @Test
    fun `eight flowers belongs to the revealing player`() {
        val table = tableWithFlowers(8, 0, 0, 0)
        val winner = table.players[0]
        val revealed = flowersOf(winner.id, table).last()

        val opportunity = TaiwanSpecialFlowerWin.findOpportunity(table, winner.id, revealed)

        requireNotNull(opportunity)
        assertEquals(TaiwanTaiType.EightFlowers, opportunity.taiResult.type)
        assertEquals(winner.id, opportunity.winnerId)
        assertNull(opportunity.payerId)
        assertEquals(3, TaiwanSpecialFlowerWin.settle(table, opportunity).paymentsByPlayerId.size)
    }

    /** 已有七花的玩家可以搶走另一家剛公開的最後一張花。 */
    @Test
    fun `seven flower holder claims the newly revealed flower`() {
        val table = tableWithFlowers(7, 1, 0, 0)
        val winner = table.players[0]
        val revealer = table.players[1]
        val revealed = flowersOf(revealer.id, table).single()

        val opportunity = TaiwanSpecialFlowerWin.findOpportunity(table, revealer.id, revealed)

        requireNotNull(opportunity)
        assertEquals(TaiwanTaiType.SevenFlowers, opportunity.taiResult.type)
        assertEquals(winner.id, opportunity.winnerId)
        assertEquals(revealer.id, opportunity.payerId)
        assertEquals(revealed, opportunity.claimedFlower)
    }

    /** 公開第七花的玩家可以搶走另一家先前公開的唯一花牌。 */
    @Test
    fun `seventh flower revealer claims the existing public flower`() {
        val table = tableWithFlowers(7, 1, 0, 0)
        val revealer = table.players[0]
        val payer = table.players[1]
        val revealed = flowersOf(revealer.id, table).last()
        val claimed = flowersOf(payer.id, table).single()

        val opportunity = TaiwanSpecialFlowerWin.findOpportunity(table, revealer.id, revealed)

        requireNotNull(opportunity)
        assertEquals(revealer.id, opportunity.winnerId)
        assertEquals(payer.id, opportunity.payerId)
        assertEquals(claimed, opportunity.claimedFlower)
    }

    /** 不足七張花時不建立特殊花胡資格。 */
    @Test
    fun `ordinary flower counts do not create an opportunity`() {
        val table = tableWithFlowers(6, 1, 1, 0)
        val revealer = table.players[0]

        assertNull(
            TaiwanSpecialFlowerWin.findOpportunity(
                tableState = table,
                revealingPlayerId = revealer.id,
                revealedFlower = flowersOf(revealer.id, table).last(),
            ),
        )
    }

    /** 建立依座位分配指定花牌數量的台灣麻將桌況。 */
    private fun tableWithFlowers(vararg counts: Int): TableState {
        val base = FakeTableStateFactory.create(
            players = Wind.entries.map(FakeMahjongPlayerFactory::create),
            config = TaiwanRuleConfig(),
        )
        val flowers = flowerTiles()
        var offset = 0
        val players = base.players.mapIndexed { index, player ->
            val count = counts[index]
            val assigned = flowers.subList(offset, offset + count)
            offset += count
            player.copy(tileAreas = PlayerTileAreas.EMPTY.withArea(TaiwanPlayerTileAreaIds.FLOWERS, assigned))
        }
        return base.copy(players = players)
    }

    /** 依固定花牌種類建立八張互不相同的測試牌。 */
    private fun flowerTiles(): List<IdentifiedTile> = TaiwanTileTypes.ALL.map { typeId ->
        FakeIdentifiedTileFactory.create(Tile.Extension(typeId))
    }

    /** 取得指定玩家的公開花牌。 */
    private fun flowersOf(playerId: Uuid, table: TableState): List<IdentifiedTile> = table.players
        .first { it.id == playerId }
        .tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]
        ?.tiles
        .orEmpty()
}
