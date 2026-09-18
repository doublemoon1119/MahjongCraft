package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropKinds
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatAnchor
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證日麻桌上點棒的描述內容，以及換算出的位置與原本的擺法相同。 */
class RiichiTablePropsTest {
    private fun state(
        riichiSeatIndices: Set<Int> = emptySet(),
        dealerSeatIndex: Int = 0,
        comboCount: Int = 0,
        stickPotCount: Int = 0,
    ): TableState {
        val players = Wind.entries.mapIndexed { seatIndex, wind ->
            FakeMahjongPlayerFactory.create(
                seatWind = wind,
                playerRuleState = RiichiPlayerState(
                    riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East).takeIf { seatIndex in riichiSeatIndices },
                ),
            )
        }
        return FakeTableStateFactory.create(
            players = players,
            dealerPlayerId = players[dealerSeatIndex].id,
            comboCount = comboCount,
            dynamicRuleState = RiichiDynamicState(riichiStickCount = stickPotCount),
        )
    }

    private fun TablePropPlacement.place(facing: MahjongTableFacing): MahjongTileWallPlacement = MahjongTileTableLayout.seatAnchorPlacement(
        controllerX = CONTROLLER_X,
        controllerY = CONTROLLER_Y,
        controllerZ = CONTROLLER_Z,
        tableFacing = facing,
        seatIndex = seatIndex,
        anchor = anchor,
        offset = offset,
        yawOffset = yawOffset,
    )

    private fun assertSamePlacement(expected: MahjongTileWallPlacement, actual: MahjongTileWallPlacement, context: String) {
        assertTrue(abs(expected.x - actual.x) < EPSILON, "$context x: $expected vs $actual")
        assertTrue(abs(expected.y - actual.y) < EPSILON, "$context y: $expected vs $actual")
        assertTrue(abs(expected.z - actual.z) < EPSILON, "$context z: $expected vs $actual")
        assertTrue(abs(expected.yaw - actual.yaw) < EPSILON, "$context yaw: $expected vs $actual")
    }

    /** 立直中的玩家各一根千點棒，放在自己的牌河內緣。 */
    @Test
    fun `each riichi player gets a thousand stick at their discards`() {
        val props = RiichiTableProps.describe(state(riichiSeatIndices = setOf(1, 3), stickPotCount = 2))

        assertEquals(listOf(1, 3), props.map { it.seatIndex })
        assertTrue(props.all { it.anchor == TableSeatAnchor.DISCARD_INNER_EDGE && it.variant == "1000" })
        assertTrue(props.all { it.kind == BuiltInTablePropKinds.SCORING_STICK })
    }

    /** 莊家角落先疊本場棒、再接延續的供託；延續支數是供託總數扣掉這局立直中的人數。 */
    @Test
    fun `the dealer corner stacks combo sticks before the carried-over pot`() {
        val props = RiichiTableProps.describe(state(riichiSeatIndices = setOf(0), dealerSeatIndex = 2, comboCount = 2, stickPotCount = 3))
        val corner = props.filter { it.anchor == TableSeatAnchor.MELD_CORNER }

        assertTrue(corner.all { it.seatIndex == 2 })
        assertEquals(listOf("100", "100", "1000", "1000"), corner.map { it.variant })
    }

    /** 沒有連莊、沒有供託、沒有人立直時，桌上沒有任何點棒。 */
    @Test
    fun `an empty table has no sticks`() {
        assertEquals(emptyList(), RiichiTableProps.describe(state()))
    }

    /**
     * 立直棒的世界座標與朝向（controller 位於 12, 64, -7；四種桌子朝向、四個座位）。
     *
     * 期望值取自搬移前的立直棒擺放函式，確保搬移後位置完全不變。
     */
    @Test
    fun `riichi sticks keep their established position`() {
        val expected = mapOf(
            (MahjongTableFacing.NORTH to 0) to MahjongTileWallPlacement(12.5, 65.0, -6.18625, 0.0f),
            (MahjongTableFacing.NORTH to 1) to MahjongTileWallPlacement(12.81375, 65.0, -6.5, 270.0f),
            (MahjongTableFacing.NORTH to 2) to MahjongTileWallPlacement(12.5, 65.0, -6.81375, 180.0f),
            (MahjongTableFacing.NORTH to 3) to MahjongTileWallPlacement(12.18625, 65.0, -6.5, 90.0f),
            (MahjongTableFacing.EAST to 0) to MahjongTileWallPlacement(12.18625, 65.0, -6.5, 90.0f),
            (MahjongTableFacing.EAST to 1) to MahjongTileWallPlacement(12.5, 65.0, -6.18625, 0.0f),
            (MahjongTableFacing.EAST to 2) to MahjongTileWallPlacement(12.81375, 65.0, -6.5, 270.0f),
            (MahjongTableFacing.EAST to 3) to MahjongTileWallPlacement(12.5, 65.0, -6.81375, 180.0f),
            (MahjongTableFacing.SOUTH to 0) to MahjongTileWallPlacement(12.5, 65.0, -6.81375, 180.0f),
            (MahjongTableFacing.SOUTH to 1) to MahjongTileWallPlacement(12.18625, 65.0, -6.5, 90.0f),
            (MahjongTableFacing.SOUTH to 2) to MahjongTileWallPlacement(12.5, 65.0, -6.18625, 0.0f),
            (MahjongTableFacing.SOUTH to 3) to MahjongTileWallPlacement(12.81375, 65.0, -6.5, 270.0f),
            (MahjongTableFacing.WEST to 0) to MahjongTileWallPlacement(12.81375, 65.0, -6.5, 270.0f),
            (MahjongTableFacing.WEST to 1) to MahjongTileWallPlacement(12.5, 65.0, -6.81375, 180.0f),
            (MahjongTableFacing.WEST to 2) to MahjongTileWallPlacement(12.18625, 65.0, -6.5, 90.0f),
            (MahjongTableFacing.WEST to 3) to MahjongTileWallPlacement(12.5, 65.0, -6.18625, 0.0f),
        )
        expected.forEach { (key, placement) ->
            val (facing, seatIndex) = key
            val stick = RiichiTableProps.describe(state(riichiSeatIndices = setOf(seatIndex), stickPotCount = 1)).single()
            assertSamePlacement(placement, stick.place(facing), "facing=$facing seat=$seatIndex")
        }
    }

    /**
     * 角落那一疊的世界座標與朝向（controller 位於 12, 64, -7；桌子朝北；莊家座位 1）：一排四根，第五根起往上疊。
     *
     * 期望值取自搬移前的積棒擺放函式，確保搬移後位置完全不變。
     */
    @Test
    fun `corner sticks keep their established position`() {
        val expected = listOf(
            MahjongTileWallPlacement(13.6875, 65.0, -7.855, 0.0f),
            MahjongTileWallPlacement(13.6875, 65.0, -7.79, 0.0f),
            MahjongTileWallPlacement(13.6875, 65.0, -7.725, 0.0f),
            MahjongTileWallPlacement(13.6875, 65.0, -7.66, 0.0f),
            MahjongTileWallPlacement(13.6875, 65.015, -7.855, 0.0f),
            MahjongTileWallPlacement(13.6875, 65.015, -7.79, 0.0f),
        )
        val corner = RiichiTableProps.describe(state(dealerSeatIndex = 1, comboCount = 3, stickPotCount = 3))

        assertEquals(expected.size, corner.size)
        corner.zip(expected).forEachIndexed { stackIndex, (stick, placement) ->
            assertSamePlacement(placement, stick.place(MahjongTableFacing.NORTH), "stack=$stackIndex")
        }
    }

    private companion object {
        const val CONTROLLER_X = 12
        const val CONTROLLER_Y = 64
        const val CONTROLLER_Z = -7
        const val EPSILON = 1.0e-6
    }
}
