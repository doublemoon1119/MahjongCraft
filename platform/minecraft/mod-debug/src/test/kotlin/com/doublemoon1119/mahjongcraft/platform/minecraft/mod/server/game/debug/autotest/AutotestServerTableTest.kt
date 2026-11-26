package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongTableStructure
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 驗證伺服器自動測試的牌桌：機器人站位不會擋住任何一張測試牌桌，每一場對局使用固定的種子。 */
class AutotestServerTableTest {
    /** 每位機器人站的方塊都不在任何一張牌桌、任何朝向的結構範圍內。 */
    @Test
    fun `bots stand outside every table`() {
        val tables = AutotestServerTable.all()
        val tableBlocks = tables.flatMap { table ->
            Direction.Plane.HORIZONTAL.flatMap { facing -> MahjongTableStructure.placements(table.position, facing).values }
        }.toSet()
        tables.forEach { table ->
            val standing = BlockPos.containing(table.standingPosition)
            val body = listOf(standing, standing.above())
            assertTrue(body.none(tableBlocks::contains), "${table.botName} stands at $standing inside a table")
        }
    }

    /** 同一張牌桌的同一場對局每次得到相同的種子，測試才會每次打同一場對局。 */
    @Test
    fun `a match gets the same seed every time`() {
        val first = AutotestServerTable.all()
        val second = AutotestServerTable.all()

        first.zip(second).forEach { (table, again) ->
            listOf(0, 1).forEach { matchIndex ->
                assertEquals(table.matchSeed(matchIndex), again.matchSeed(matchIndex), "${table.rule} match $matchIndex changed its seed")
            }
        }
    }

    /** 規則不同或種子編號不同時種子不同，各桌不會發到同一副牌。 */
    @Test
    fun `seeds differ by rule and by seed number`() {
        val riichi = AutotestServerTable(rule = "mahjongcraft:riichi", botName = "Bot", position = BlockPos.ZERO, seedNumbers = listOf(1, 2))
        val taiwan = AutotestServerTable(rule = "mahjongcraft:taiwan", botName = "Bot", position = BlockPos.ZERO, seedNumbers = listOf(1, 1))

        assertNotEquals(riichi.matchSeed(0), riichi.matchSeed(1), "Different seed numbers gave the same seed")
        assertNotEquals(riichi.matchSeed(0), taiwan.matchSeed(0), "Different rules gave the same seed")
        assertEquals(taiwan.matchSeed(0), taiwan.matchSeed(1), "The same rule and seed number gave different seeds")
    }
}
