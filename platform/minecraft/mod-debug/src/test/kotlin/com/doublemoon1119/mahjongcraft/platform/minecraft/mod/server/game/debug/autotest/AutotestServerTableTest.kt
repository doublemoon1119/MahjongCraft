package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongTableStructure
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import kotlin.test.Test
import kotlin.test.assertTrue

/** 驗證伺服器自動測試的機器人站位不會擋住任何一張測試牌桌。 */
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
}
