package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOrientation
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatAnchor
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatOffset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/** [MahjongTileTableLayout] 的墩位間距、四面旋轉、角落不重疊與輸入驗證測試。 */
class MahjongTileTableLayoutTest {
    /** 牌牆大小直接向規則查詢，不需要開局時排出的牌牆結構；台麻依是否使用花牌決定。 */
    @Test
    fun `wall size comes from the rule without an opening record`() {
        assertEquals(17, MahjongTileTableLayout.wallStacksPerSide(RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig()), 4))
        assertEquals(
            expected = 18,
            actual = MahjongTileTableLayout.wallStacksPerSide(
                module = ThreePlayerRiichiRuleModule(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, ThreePlayerRiichiRuleConfig()),
                playerCount = 3,
            ),
        )
        assertEquals(
            expected = 18,
            actual = MahjongTileTableLayout.wallStacksPerSide(TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig(useFlowerTiles = true)), 4),
        )
        assertEquals(
            expected = 17,
            actual = MahjongTileTableLayout.wallStacksPerSide(TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig(useFlowerTiles = false)), 4),
        )
    }

    /**
     * 同一面內不同墩應等距排列，`stack` 0 在最大 X（該面玩家自己右手邊）、遞增時往負向移動；整條線
     *  額外偏移半個牌高（見 [MahjongTileTableLayout] 角落互相接角的說明），不是置中於側面正中央。 */
    @Test
    fun `stacks are evenly spaced and shifted for corner interlock`() {
        val first = wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 0))
        val last = wallPlacement(position = TileWallPosition(side = 0, stack = STACKS_PER_SIDE - 1, layer = 0))
        val middle = wallPlacement(position = TileWallPosition(side = 0, stack = (STACKS_PER_SIDE - 1) / 2, layer = 0))
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val cornerInterlockShift = MahjongTileDimensions.TILE_HEIGHT / 2.0 +
            MahjongTileDimensions.TILE_WIDTH * MahjongTileTableLayout.CORNER_GAP_RATIO

        assertEquals((STACKS_PER_SIDE - 1) * stackStep, first.x - last.x, ABSOLUTE_TOLERANCE)
        assertEquals(cornerInterlockShift, middle.x - CONTROLLER_CENTER_X, ABSOLUTE_TOLERANCE)
        assertEquals(first.z, last.z, ABSOLUTE_TOLERANCE)
    }

    /** 桌面牌位配置讓整面牌牆往桌心移動，並往 `stack = 0` 那一端錯開；同面內的間距不變。 */
    @Test
    fun `table tile layout moves the wall toward the centre and along its row`() {
        val tileLayout = TableTileLayoutProfile(wallInset = 0.07, wallCornerOverhang = 0.18)
        val position = TileWallPosition(side = 0, stack = 3, layer = 1)
        val standard = wallPlacement(position = position)
        val moved = wallPlacement(position = position, tileLayout = tileLayout)

        assertEquals(0.07, abs(standard.z - CONTROLLER_CENTER_Z) - abs(moved.z - CONTROLLER_CENTER_Z), ABSOLUTE_TOLERANCE)
        assertEquals(0.18, moved.x - standard.x, ABSOLUTE_TOLERANCE)
        assertEquals(standard.y, moved.y, ABSOLUTE_TOLERANCE)
        assertEquals(standard.yaw, moved.yaw)
    }

    /**
     * 往左錯開時整面牌牆是往右錯開的鏡像：同一面牌牆中第幾墩的位置左右對調，轉角伸出的那一端換到另一邊；
     * 手牌那一列避開牌牆後的可用範圍也左右對調。
     */
    @Test
    fun `left stagger mirrors the right stagger`() {
        val right = TableTileLayoutProfile(wallInset = 0.105, wallStagger = TableWallStagger.RIGHT, wallCornerOverhang = 0.18)
        val left = right.copy(wallStagger = TableWallStagger.LEFT)
        (0 until STACKS_PER_SIDE).forEach { stack ->
            val rightPlacement = wallPlacement(position = TileWallPosition(side = 0, stack = STACKS_PER_SIDE - 1 - stack, layer = 0), tileLayout = right)
            val leftPlacement = wallPlacement(position = TileWallPosition(side = 0, stack = stack, layer = 0), tileLayout = left)

            assertEquals(CONTROLLER_CENTER_X - rightPlacement.x, leftPlacement.x - CONTROLLER_CENTER_X, ABSOLUTE_TOLERANCE)
            assertEquals(rightPlacement.z, leftPlacement.z, ABSOLUTE_TOLERANCE)
        }
        val band = MahjongTileTableLayout.handRowBand()
        val rightSpan = MahjongTileTableLayout.rowSpan(fullWallClearance(right, STACKS_PER_SIDE), band.left, band.right)
        val leftSpan = MahjongTileTableLayout.rowSpan(fullWallClearance(left, STACKS_PER_SIDE), band.left, band.right)

        assertEquals(-rightSpan.right, leftSpan.left, ABSOLUTE_TOLERANCE)
        assertEquals(-rightSpan.left, leftSpan.right, ABSOLUTE_TOLERANCE)
    }

    /** 牌牆配置的距離不能是負數或非有限值。 */
    @Test
    fun `table tile layout rejects invalid distances`() {
        assertFailsWith<IllegalArgumentException> { TableTileLayoutProfile(wallInset = -0.01) }
        assertFailsWith<IllegalArgumentException> { TableTileLayoutProfile(wallCornerOverhang = Double.NaN) }
    }

    /** 手牌那一列的中央錨點與單張手牌的位置一致。 */
    @Test
    fun `hand row centre anchor matches a single hand tile`() {
        val seatLayout = MahjongTableSeatLayout.forPlayerCount(4)
        (0 until 4).forEach { seat ->
            val anchor = MahjongTileTableLayout.seatAnchorPlacement(
                controllerX = 10,
                controllerY = 64,
                controllerZ = -4,
                tableFacing = MahjongTableFacing.EAST,
                seatLayout = seatLayout,
                seatIndex = seat,
                anchor = TableSeatAnchor.HAND_ROW_CENTER,
                offset = TableSeatOffset(),
            )
            val hand = MahjongTileTableLayout.handPlacement(
                controllerX = 10,
                controllerY = 64,
                controllerZ = -4,
                tableFacing = MahjongTableFacing.EAST,
                seatLayout = seatLayout,
                seatIndex = seat,
                handSize = 1,
                tileIndex = 0,
            )
            assertEquals(hand.x, anchor.x, ABSOLUTE_TOLERANCE)
            assertEquals(hand.z, anchor.z, ABSOLUTE_TOLERANCE)
            assertEquals(hand.yaw, anchor.yaw)
        }
    }

    /**
     * 四面牌牆的所有墩（含四個角落）躺平後的世界 footprint 彼此都不該重疊——角落偏移量必須
     * 考慮牌本身的寬度，不能只讓端點座標重合。footprint 沿墩排列方向的
     * 半寬是 [MahjongTileDimensions.TILE_WIDTH]／2，垂直於側面方向的半寬是
     * [MahjongTileDimensions.TILE_HEIGHT]／2（牌立起時的高度，躺平後變成水平方向的寬度），依 yaw
     * 判斷這張牌的兩個方向何者對應世界 X／Z。允許邊界剛好貼齊（觸碰但不重疊），只有兩個 footprint
     * 各自的 X 範圍與 Z 範圍都有正的重疊時才視為真正碰撞。
     */
    @Test
    fun `wall corners interlock without overlapping across sides`() {
        val footprints = (0 until SIDE_COUNT).flatMap { side ->
            (0 until STACKS_PER_SIDE).map { stack ->
                wallPlacement(position = TileWallPosition(side = side, stack = stack, layer = 0)).toFootprint()
            }
        }

        for (i in footprints.indices) {
            for (j in i + 1 until footprints.size) {
                assertFalse(footprints[i].overlaps(footprints[j]), "Tile footprints $i and $j overlap: ${footprints[i]} vs ${footprints[j]}")
            }
        }
    }

    /**
     * 三面牌牆（三人對局）的第 `side` 面擺在座位 `莊家 - side` 面前，跟四面牌牆中同一個座位面前的那一面位置相同；
     * 空座位那一側沒有牌牆。
     */
    @Test
    fun `three sided wall sits in front of the three seated players`() {
        (0 until THREE_PLAYER_SIDE_COUNT).forEach { dealer ->
            (0 until THREE_PLAYER_SIDE_COUNT).forEach { side ->
                val seat = (dealer - side).mod(THREE_PLAYER_SIDE_COUNT)
                val fourSidedSide = (dealer - seat).mod(SIDE_COUNT)
                val position = TileWallPosition(side = side, stack = 3, layer = 0)
                val threeSided = wallPlacement(
                    dealerSeatIndex = dealer,
                    position = position,
                    stacksPerSide = THREE_PLAYER_STACKS_PER_SIDE,
                    sideCount = THREE_PLAYER_SIDE_COUNT,
                )
                val fourSided = wallPlacement(
                    dealerSeatIndex = dealer,
                    position = position.copy(side = fourSidedSide),
                    stacksPerSide = THREE_PLAYER_STACKS_PER_SIDE,
                )
                assertEquals(fourSided, threeSided, "dealer=$dealer side=$side")
            }
        }
    }

    /** 三面牌牆每面 18 墩時，任兩張牌的 footprint 都不重疊。 */
    @Test
    fun `three sided wall tiles do not overlap`() {
        val footprints = (0 until THREE_PLAYER_SIDE_COUNT).flatMap { side ->
            (0 until THREE_PLAYER_STACKS_PER_SIDE).map { stack ->
                wallPlacement(
                    position = TileWallPosition(side = side, stack = stack, layer = 0),
                    stacksPerSide = THREE_PLAYER_STACKS_PER_SIDE,
                    sideCount = THREE_PLAYER_SIDE_COUNT,
                ).toFootprint()
            }
        }

        for (i in footprints.indices) {
            for (j in i + 1 until footprints.size) {
                assertFalse(footprints[i].overlaps(footprints[j]), "Tile footprints $i and $j overlap")
            }
        }
    }

    /** 兩面短牌牆不論相鄰或相對、桌子朝向與牌張層數，都要避開中央擲骰區。 */
    @Test
    fun `short two-sided walls keep the dice area clear`() {
        val centerX = CONTROLLER_CENTER_X
        val centerZ = CONTROLLER_CENTER_Z
        val minimumClearDistance = 0.3 + MahjongTileDimensions.TILE_HEIGHT / 2.0
        val arrangements = listOf(listOf(0, 1), listOf(0, 2))

        MahjongTableFacing.entries.forEach { facing ->
            arrangements.forEach { quarterTurns ->
                (0..1).forEach { layer ->
                    (0 until 2).forEach { side ->
                        (0 until 4).forEach { stack ->
                            val placement = wallPlacement(
                                tableFacing = facing,
                                position = TileWallPosition(side = side, stack = stack, layer = layer),
                                stacksPerSide = 4,
                                sideCount = 2,
                                quarterTurns = quarterTurns,
                            )
                            val distance = hypot(placement.x - centerX, placement.z - centerZ)
                            assertEquals(
                                expected = true,
                                actual = distance > minimumClearDistance,
                                message = "Short wall placement $placement must clear the central dice area",
                            )
                        }
                    }
                }
            }
        }
    }

    /** 標準 17 墩與 18 墩牌牆仍使用各自原本的外側距離。 */
    @Test
    fun `standard wall spans remain unchanged`() {
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val seventeen = wallPlacement(
            position = TileWallPosition(side = 0, stack = 8, layer = 0),
            stacksPerSide = 17,
        )
        val eighteen = wallPlacement(
            position = TileWallPosition(side = 0, stack = 9, layer = 0),
            stacksPerSide = 18,
        )

        assertEquals(17 / 2.0 * stackStep, seventeen.z - CONTROLLER_CENTER_Z, ABSOLUTE_TOLERANCE)
        assertEquals(18 / 2.0 * stackStep, eighteen.z - CONTROLLER_CENTER_Z, ABSOLUTE_TOLERANCE)
    }

    /** 上層應比下層高出一個牌深，水平位置不變。 */
    @Test
    fun `upper layer sits one tile depth above lower layer`() {
        val lower = wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 0))
        val upper = wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 1))

        assertEquals(lower.x, upper.x, ABSOLUTE_TOLERANCE)
        assertEquals(lower.z, upper.z, ABSOLUTE_TOLERANCE)
        val expectedLayerHeight = MahjongTileDimensions.TILE_DEPTH + MahjongTileDimensions.TILE_SMALL_PADDING
        assertEquals(expectedLayerHeight, upper.y - lower.y, ABSOLUTE_TOLERANCE)
    }

    /** 改變 [TileWallPosition.side] 應繞莊家局部南側旋轉 90 度一步。 */
    @Test
    fun `side rotates around dealer local south`() {
        val south = wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 0))
        val west = wallPlacement(position = TileWallPosition(side = 1, stack = 0, layer = 0))

        assertEquals(-(south.z - CONTROLLER_CENTER_Z), west.x - CONTROLLER_CENTER_X, ABSOLUTE_TOLERANCE)
        assertEquals(south.x - CONTROLLER_CENTER_X, west.z - CONTROLLER_CENTER_Z, ABSOLUTE_TOLERANCE)
    }

    /** 桌子朝東時，局部座標還應再旋轉至世界東向。 */
    @Test
    fun `table facing rotates local placement into world`() {
        val northFacing = wallPlacement(tableFacing = MahjongTableFacing.NORTH)
        val eastFacing = wallPlacement(tableFacing = MahjongTableFacing.EAST)
        val northOffsetX = northFacing.x - CONTROLLER_CENTER_X
        val northOffsetZ = northFacing.z - CONTROLLER_CENTER_Z

        assertEquals(-northOffsetZ, eastFacing.x - CONTROLLER_CENTER_X, ABSOLUTE_TOLERANCE)
        assertEquals(northOffsetX, eastFacing.z - CONTROLLER_CENTER_Z, ABSOLUTE_TOLERANCE)
        assertNotEquals(northFacing.yaw, eastFacing.yaw)
    }

    /** 莊家不同座位時，同一個 [TileWallPosition] 應落在不同的世界局部側面。 */
    @Test
    fun `dealer seat index rotates wall side`() {
        val dealerZero = wallPlacement(dealerSeatIndex = 0)
        val dealerOne = wallPlacement(dealerSeatIndex = 1)

        assertNotEquals(dealerZero.x to dealerZero.z, dealerOne.x to dealerOne.z)
    }

    /** 沿牌牆的小數位移應在同一面相鄰墩位之間線性內插。 */
    @Test
    fun `along wall offset interpolates between adjacent stacks`() {
        val position = TileWallPosition(side = 0, stack = 0, layer = 0)
        val start = wallPlacement(position = position)
        val end = wallPlacement(position = position.copy(stack = 1))
        val shifted = wallPlacement(
            placement = TileWallPlacement(position, TileWallPlacementOffset(alongWallStacks = 0.25)),
        )

        assertEquals(start.x + (end.x - start.x) * 0.25, shifted.x, ABSOLUTE_TOLERANCE)
        assertEquals(start.z + (end.z - start.z) * 0.25, shifted.z, ABSOLUTE_TOLERANCE)
    }

    /** 同一面保留牌軌道套用分界位移後應保持一致 yaw，且相鄰牌墩的實際 footprint 不重疊。 */
    @Test
    fun `single side reserved track keeps yaw and spacing`() {
        MahjongTableFacing.entries.forEach { facing ->
            repeat(SIDE_COUNT) { side ->
                val placements = (15 downTo 6).map { stack ->
                    wallPlacement(
                        tableFacing = facing,
                        placement = TileWallPlacement(
                            position = TileWallPosition(side, stack, 0),
                            offset = TileWallPlacementOffset(alongWallStacks = 0.25),
                        ),
                    )
                }

                assertEquals(1, placements.map { it.yaw }.distinct().size)
                placements.zipWithNext().forEach { (left, right) ->
                    assertFalse(left.toFootprint().overlaps(right.toFootprint()))
                }
            }
        }
    }

    /** 沿牌牆的小數位移跨過牆角時，終點應在兩面相鄰墩位之間連續內插。 */
    @Test
    fun `along wall offset crosses a corner continuously`() {
        val position = TileWallPosition(side = 0, stack = STACKS_PER_SIDE - 1, layer = 0)
        val start = wallPlacement(position = position)
        val nextSide = wallPlacement(position = TileWallPosition(side = 1, stack = 0, layer = 0))
        val shifted = wallPlacement(
            placement = TileWallPlacement(position, TileWallPlacementOffset(alongWallStacks = 0.5)),
        )

        assertEquals((start.x + nextSide.x) / 2.0, shifted.x, ABSOLUTE_TOLERANCE)
        assertEquals((start.z + nextSide.z) / 2.0, shifted.z, ABSOLUTE_TOLERANCE)
    }

    /** 朝桌心的正規化位移應縮短一個牌高的水平半徑，不改變高度。 */
    @Test
    fun `toward center offset follows the local radial direction`() {
        val position = TileWallPosition(side = 0, stack = 3, layer = 0)
        val base = wallPlacement(position = position)
        val shifted = wallPlacement(
            placement = TileWallPlacement(position, TileWallPlacementOffset(towardTableCenterTiles = 1.0)),
        )
        val baseRadius = hypot(base.x - CONTROLLER_CENTER_X, base.z - CONTROLLER_CENTER_Z)
        val shiftedRadius = hypot(shifted.x - CONTROLLER_CENTER_X, shifted.z - CONTROLLER_CENTER_Z)

        assertEquals(MahjongTileDimensions.TILE_HEIGHT, baseRadius - shiftedRadius, ABSOLUTE_TOLERANCE)
        assertEquals(base.y, shifted.y, ABSOLUTE_TOLERANCE)
    }

    /** 垂直正規化位移一層應等於一個牌深與層間縫隙的合計高度。 */
    @Test
    fun `upward offset uses the wall layer step`() {
        val position = TileWallPosition(side = 0, stack = 2, layer = 0)
        val base = wallPlacement(position = position)
        val shifted = wallPlacement(
            placement = TileWallPlacement(position, TileWallPlacementOffset(upwardLayers = 1.0)),
        )

        assertEquals(
            expected = MahjongTileDimensions.TILE_DEPTH + MahjongTileDimensions.TILE_SMALL_PADDING,
            actual = shifted.y - base.y,
            absoluteTolerance = ABSOLUTE_TOLERANCE,
        )
    }

    /** 離散方向應相對基本墩位旋轉，不影響其世界座標。 */
    @Test
    fun `orientation rotates relative to the base wall yaw`() {
        val position = TileWallPosition(side = 2, stack = 4, layer = 0)
        val base = wallPlacement(position = position)
        val yawOffsets = mapOf(
            TileWallPlacementOrientation.DEFAULT to 0.0f,
            TileWallPlacementOrientation.CLOCKWISE_90 to 90.0f,
            TileWallPlacementOrientation.HALF_TURN to 180.0f,
            TileWallPlacementOrientation.COUNTERCLOCKWISE_90 to -90.0f,
        )

        yawOffsets.forEach { (orientation, yawOffset) ->
            val rotated = wallPlacement(placement = TileWallPlacement(position, orientation = orientation))
            assertEquals(base.x, rotated.x, ABSOLUTE_TOLERANCE)
            assertEquals(base.y, rotated.y, ABSOLUTE_TOLERANCE)
            assertEquals(base.z, rotated.z, ABSOLUTE_TOLERANCE)
            assertEquals((base.yaw + yawOffset).mod(360.0f), rotated.yaw, ABSOLUTE_TOLERANCE.toFloat())
        }
    }

    /** 四種桌子朝向都應保留規則 offset 的距離與高度，不把局部軸誤當成固定世界軸。 */
    @Test
    fun `placement offsets rotate consistently for every table facing`() {
        val position = TileWallPosition(side = 1, stack = 5, layer = 0)
        MahjongTableFacing.entries.forEach { facing ->
            val base = wallPlacement(tableFacing = facing, position = position)
            val shifted = wallPlacement(
                tableFacing = facing,
                placement = TileWallPlacement(
                    position = position,
                    offset = TileWallPlacementOffset(towardTableCenterTiles = 0.5, upwardLayers = 0.5),
                ),
            )
            val horizontalDistance = hypot(shifted.x - base.x, shifted.z - base.z)

            assertEquals(MahjongTileDimensions.TILE_HEIGHT * 0.5, horizontalDistance, ABSOLUTE_TOLERANCE)
            assertEquals(
                expected = (MahjongTileDimensions.TILE_DEPTH + MahjongTileDimensions.TILE_SMALL_PADDING) * 0.5,
                actual = shifted.y - base.y,
                absoluteTolerance = ABSOLUTE_TOLERANCE,
            )
        }
    }

    /** 超出墩數或層數範圍應直接拒絕，不產生不合理座標。 */
    @Test
    fun `layout rejects out of range stack or layer`() {
        assertFailsWith<IllegalArgumentException> {
            wallPlacement(position = TileWallPosition(side = 0, stack = STACKS_PER_SIDE, layer = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 2))
        }
    }

    /** 手牌應等距排列、對稱置中，`tileIndex` 0 在最大 X、遞增時往負向移動，跟牌牆 `stack` 排列方向一致。 */
    @Test
    fun `hand tiles are evenly spaced and centered`() {
        val first = handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, tileIndex = 0)
        val last = handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, tileIndex = HAND_SIZE - 1)
        val middle = handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, tileIndex = (HAND_SIZE - 1) / 2)
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING

        assertEquals((HAND_SIZE - 1) * stackStep, first.x - last.x, ABSOLUTE_TOLERANCE)
        assertEquals(CONTROLLER_CENTER_X, middle.x, ABSOLUTE_TOLERANCE)
        assertEquals(first.z, last.z, ABSOLUTE_TOLERANCE)
    }

    /** 手牌垂直於側面的距離應比牌牆更靠桌緣（比 `halfSpan` 更遠離桌子中心）。 */
    @Test
    fun `hand tiles sit closer to the table edge than the wall`() {
        val hand = handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, tileIndex = 0)
        val wall = wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 0))
        val centerZ = CONTROLLER_CENTER_Z

        assertEquals(true, (hand.z - centerZ) > (wall.z - centerZ), "Hand offset ${hand.z - centerZ} should exceed wall offset ${wall.z - centerZ}")
    }

    /**
     * 不同座位 index 應落在不同的世界局部側面，且不經過莊家相對旋轉（跟牌牆不同）。座位 1 落在
     * EAST（`seatIndexToTableSide` 的順時針方向，跟牌牆自身組裝用的逆時針 `SIDE_ORDER` 是兩套獨立
     * 方向，見 `seatIndexToTableSide` KDoc），對應 [rotateForSide] 的 EAST 公式 `(z, -x)`。
     */
    @Test
    fun `hand seat index selects physical side directly`() {
        val seatZero = handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, seatIndex = 0)
        val seatOne = handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, seatIndex = 1)

        assertEquals(seatZero.z - CONTROLLER_CENTER_Z, seatOne.x - CONTROLLER_CENTER_X, ABSOLUTE_TOLERANCE)
        assertEquals(-(seatZero.x - CONTROLLER_CENTER_X), seatOne.z - CONTROLLER_CENTER_Z, ABSOLUTE_TOLERANCE)
    }

    /** 超出手牌張數範圍應直接拒絕。 */
    @Test
    fun `hand layout rejects out of range tile index`() {
        assertFailsWith<IllegalArgumentException> { handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, tileIndex = HAND_SIZE) }
        assertFailsWith<IllegalArgumentException> { handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, tileIndex = -1) }
    }

    /** 摸牌位應落在立牌列尾端（`tileIndex = 0`，玩家右手邊）外一段看得出來的縫隙，不是緊貼。 */
    @Test
    fun `drawn tile sits beyond the hand row edge with a visible gap`() {
        val firstStanding = handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, handSize = HAND_SIZE, tileIndex = 0)
        val drawn = drawnTilePlacement(MahjongTableSeatLayout.FOUR_PLAYER, standingTileCount = HAND_SIZE)
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val gap = MahjongTileDimensions.TILE_WIDTH * MahjongTileTableLayout.DRAWN_TILE_GAP_RATIO

        assertEquals(stackStep + gap, drawn.x - firstStanding.x, ABSOLUTE_TOLERANCE)
        assertEquals(firstStanding.z, drawn.z, ABSOLUTE_TOLERANCE)
    }

    /** 摸牌位垂直於側面的距離應跟手牌共用同一個 `HAND_EDGE_OFFSET`。 */
    @Test
    fun `drawn tile shares the hand row's distance from center`() {
        val hand = handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, tileIndex = 0)
        val drawn = drawnTilePlacement(MahjongTableSeatLayout.FOUR_PLAYER, standingTileCount = HAND_SIZE)

        assertEquals(hand.z, drawn.z, ABSOLUTE_TOLERANCE)
    }

    /** 牌河第 7 張（index 6）應換到下一排，跟前 6 張的垂直距離不同、第 0 欄的 X 相同。 */
    @Test
    fun `discard pile wraps to a new row after the row size`() {
        val firstOfRowOne = discardPlacement(MahjongTableSeatLayout.FOUR_PLAYER, discardIndex = 0)
        val lastOfRowOne = discardPlacement(MahjongTableSeatLayout.FOUR_PLAYER, discardIndex = MahjongTileTableLayout.DISCARD_TILES_PER_ROW - 1)
        val firstOfRowTwo = discardPlacement(MahjongTableSeatLayout.FOUR_PLAYER, discardIndex = MahjongTileTableLayout.DISCARD_TILES_PER_ROW)

        assertEquals(firstOfRowOne.z, lastOfRowOne.z, ABSOLUTE_TOLERANCE)
        assertEquals(firstOfRowOne.x, firstOfRowTwo.x, ABSOLUTE_TOLERANCE)
        assertNotEquals(firstOfRowOne.z, firstOfRowTwo.z)
    }

    /** 側身標記只改變 yaw（多轉 90 度），不改變座標。 */
    @Test
    fun `sideways marked discard tile only rotates yaw`() {
        val upright = discardPlacement(MahjongTableSeatLayout.FOUR_PLAYER, discardIndex = 0, isSidewaysMarked = false)
        val sideways = discardPlacement(MahjongTableSeatLayout.FOUR_PLAYER, discardIndex = 0, isSidewaysMarked = true)

        assertEquals(upright.x, sideways.x, ABSOLUTE_TOLERANCE)
        assertEquals(upright.y, sideways.y, ABSOLUTE_TOLERANCE)
        assertEquals(upright.z, sideways.z, ABSOLUTE_TOLERANCE)
        assertEquals(MahjongTileTableLayout.SIDEWAYS_YAW_OFFSET, ((sideways.yaw - upright.yaw) + FULL_YAW_DEGREES) % FULL_YAW_DEGREES, ABSOLUTE_TOLERANCE.toFloat())
    }

    /** 牌河整體應比牌牆更靠近桌子中心。 */
    @Test
    fun `discard pile sits closer to the table center than the wall`() {
        val discard = discardPlacement(MahjongTableSeatLayout.FOUR_PLAYER, discardIndex = 0)
        val wall = wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 0))
        val centerZ = CONTROLLER_CENTER_Z

        assertEquals(true, (discard.z - centerZ) < (wall.z - centerZ), "Discard offset ${discard.z - centerZ} should be less than wall offset ${wall.z - centerZ}")
    }

    /** 負數 discardIndex 應直接拒絕。 */
    @Test
    fun `discard layout rejects negative index`() {
        assertFailsWith<IllegalArgumentException> { discardPlacement(MahjongTableSeatLayout.FOUR_PLAYER, discardIndex = -1) }
    }

    /**
     * 牌山還有剩餘牌時，第 [MahjongTileTableLayout.DISCARD_SAFE_ROWS] 排放滿後不應該新增第四排——
     * 應該固定停在最後一個安全排，沿著同一排方向繼續延伸（`z` 不變，`x` 持續往外）。
     */
    @Test
    fun `discard pile extends the last safe row instead of adding a fourth row while wall remains`() {
        val lastSafeRowStart = (MahjongTileTableLayout.DISCARD_SAFE_ROWS - 1) * MahjongTileTableLayout.DISCARD_TILES_PER_ROW
        val lastTileOfSafeRows = discardPlacement(MahjongTableSeatLayout.FOUR_PLAYER, discardIndex = lastSafeRowStart, wallRemaining = true)
        val overflow = discardPlacement(MahjongTableSeatLayout.FOUR_PLAYER, discardIndex = lastSafeRowStart + MahjongTileTableLayout.DISCARD_TILES_PER_ROW, wallRemaining = true)

        assertEquals(lastTileOfSafeRows.z, overflow.z, ABSOLUTE_TOLERANCE)
        assertNotEquals(lastTileOfSafeRows.x, overflow.x)
    }

    /** 牌山已經摸完時，第四排應該正常往桌緣方向新增，不再固定停在最後一個安全排。 */
    @Test
    fun `discard pile adds a fourth row once the wall is exhausted`() {
        val lastSafeRowStart = (MahjongTileTableLayout.DISCARD_SAFE_ROWS - 1) * MahjongTileTableLayout.DISCARD_TILES_PER_ROW
        val lastTileOfSafeRows = discardPlacement(MahjongTableSeatLayout.FOUR_PLAYER, discardIndex = lastSafeRowStart, wallRemaining = false)
        val fourthRowFirstTile = discardPlacement(MahjongTableSeatLayout.FOUR_PLAYER, discardIndex = lastSafeRowStart + MahjongTileTableLayout.DISCARD_TILES_PER_ROW, wallRemaining = false)

        assertNotEquals(lastTileOfSafeRows.z, fourthRowFirstTile.z)
    }

    /** 角落有物件時，第一組副露與物件之間隔一個 [MahjongTileTableLayout.MELD_GROUP_GAP]；沒有物件時從桌角開始。 */
    @Test
    fun `meld start leaves a group gap after corner props only when there are any`() {
        assertEquals(0.0, MahjongTileTableLayout.meldStartOffset(0.0), ABSOLUTE_TOLERANCE)
        assertEquals(0.3 + MahjongTileTableLayout.MELD_GROUP_GAP, MahjongTileTableLayout.meldStartOffset(0.3), ABSOLUTE_TOLERANCE)
    }

    /** [MahjongTileTableLayout.handPlacement] 帶上非零 `handShift` 時，整排牌等量往玩家自己方向平移，牌與牌間距不變。 */
    @Test
    fun `hand placement shifts the whole row without compressing spacing`() {
        val shift = 0.4
        val unshifted = handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, tileIndex = 0)
        val shiftedFirst = handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, tileIndex = 0, handShift = shift)
        val shiftedLast = handPlacement(MahjongTableSeatLayout.FOUR_PLAYER, tileIndex = HAND_SIZE - 1, handShift = shift)
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING

        assertEquals(shift, unshifted.x - shiftedFirst.x, ABSOLUTE_TOLERANCE)
        assertEquals((HAND_SIZE - 1) * stackStep, shiftedFirst.x - shiftedLast.x, ABSOLUTE_TOLERANCE)
    }

    /** `stack` 每加 1，掉落動畫延遲該多加一個 `WAVE_STEP_TICKS`；`stack = 0` 完全不延遲。 */
    @Test
    fun `wall drop start delay grows linearly with stack`() {
        assertEquals(0, MahjongTileTableLayout.wallDropStartDelayTicks(0))
        assertEquals(2 * MahjongTileTableLayout.wallDropStartDelayTicks(1), MahjongTileTableLayout.wallDropStartDelayTicks(2))
    }

    /** 總動畫時長應等於最後一墩（`stack = stacksPerSide - 1`）的延遲，加上單次動畫本身的時長。 */
    @Test
    fun `wall drop animation ticks accounts for the last stack plus a full animation`() {
        val stacksPerSide = 17
        val expected = MahjongTileTableLayout.wallDropStartDelayTicks(stacksPerSide - 1) + TileMotionAnimationSpec.DEFAULT_DURATION_TICKS

        assertEquals(expected, MahjongTileTableLayout.wallDropAnimationTicks(stacksPerSide))
    }

    /** 只有一墩（或零墩）時，不需要任何 stagger，總時長就是單次動畫時長。 */
    @Test
    fun `wall drop animation ticks has no stagger for a single stack`() {
        assertEquals(TileMotionAnimationSpec.DEFAULT_DURATION_TICKS, MahjongTileTableLayout.wallDropAnimationTicks(1))
        assertEquals(TileMotionAnimationSpec.DEFAULT_DURATION_TICKS, MahjongTileTableLayout.wallDropAnimationTicks(0))
    }

    /**
     * 每多一次抓取，延遲該多加一個 [MahjongTileTableLayout.DEAL_TURN_STAGGER_TICKS]（只等上一次起飛
     * 播完，不等上一次整段落地）；`turnIndex = 0` 完全不延遲。
     */
    @Test
    fun `deal batch start delay grows by one turn stagger each time`() {
        assertEquals(0, MahjongTileTableLayout.dealBatchStartDelayTicks(0))
        assertEquals(MahjongTileTableLayout.DEAL_TURN_STAGGER_TICKS, MahjongTileTableLayout.dealBatchStartDelayTicks(1))
        assertEquals(2 * MahjongTileTableLayout.DEAL_TURN_STAGGER_TICKS, MahjongTileTableLayout.dealBatchStartDelayTicks(2))
    }

    /** 總動畫時長應等於最後一批的延遲，加上單一批次本身（起飛＋隱形間隔＋落下）與最後統一翻牌的時長。 */
    @Test
    fun `deal animation ticks accounts for the last batch plus flip`() {
        val batchCount = 4
        val expected = MahjongTileTableLayout.dealBatchStartDelayTicks(batchCount - 1) +
            MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS +
            MahjongTileTableLayout.DEAL_DROP_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_FLIP_GAP_TICKS +
            MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS

        assertEquals(expected, MahjongTileTableLayout.dealAnimationTicks(batchCount))
    }

    /** 只有一批（或零批）時，不需要任何批次間隔，總時長就是單一批次本身加上翻牌動畫的時長。 */
    @Test
    fun `deal animation ticks has no stagger for a single batch`() {
        val singleBatchDuration = MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS +
            MahjongTileTableLayout.DEAL_DROP_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_FLIP_GAP_TICKS +
            MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS

        assertEquals(singleBatchDuration, MahjongTileTableLayout.dealAnimationTicks(1))
        assertEquals(singleBatchDuration, MahjongTileTableLayout.dealAnimationTicks(0))
    }

    /** 建立固定 controller 與可覆寫輸入的測試 placement。 */
    private fun wallPlacement(
        tableFacing: MahjongTableFacing = MahjongTableFacing.NORTH,
        dealerSeatIndex: Int = 0,
        position: TileWallPosition = TileWallPosition(side = 0, stack = 0, layer = 0),
        placement: TileWallPlacement = TileWallPlacement(position),
        stacksPerSide: Int = STACKS_PER_SIDE,
        sideCount: Int = SIDE_COUNT,
        quarterTurns: List<Int> = List(sideCount) { (dealerSeatIndex - it).mod(sideCount) },
        tileLayout: TableTileLayoutProfile = TableTileLayoutProfile.STANDARD,
    ): MahjongTileWallPlacement = MahjongTileTableLayout.wallPlacement(
        controllerX = 10,
        controllerY = 64,
        controllerZ = -4,
        tableFacing = tableFacing,
        sideLayout = MahjongTileWallSideLayout.resolve(
            arrangement = TileWallSideArrangement(
                reference = TileWallSideReference.TABLE,
                quarterTurns = quarterTurns,
            ),
            dealerSide = MahjongTableSide.SOUTH,
        ),
        tileLayout = tileLayout,
        stacksPerSide = stacksPerSide,
        placement = placement,
    )

    /** 建立固定 controller 與可覆寫輸入的測試手牌 placement。 */
    private fun handPlacement(
        seatLayout: MahjongTableSeatLayout,
        tableFacing: MahjongTableFacing = MahjongTableFacing.NORTH,
        seatIndex: Int = 0,
        handSize: Int = HAND_SIZE,
        tileIndex: Int = 0,
        handShift: Double = 0.0,
    ): MahjongTileWallPlacement = MahjongTileTableLayout.handPlacement(
        controllerX = 10,
        controllerY = 64,
        controllerZ = -4,
        tableFacing = tableFacing,
        seatLayout = seatLayout,
        seatIndex = seatIndex,
        handSize = handSize,
        tileIndex = tileIndex,
        handShift = handShift,
    )

    /** 建立固定 controller 與可覆寫輸入的測試摸牌位 placement。 */
    private fun drawnTilePlacement(
        seatLayout: MahjongTableSeatLayout,
        tableFacing: MahjongTableFacing = MahjongTableFacing.NORTH,
        seatIndex: Int = 0,
        standingTileCount: Int = HAND_SIZE,
        handShift: Double = 0.0,
    ): MahjongTileWallPlacement = MahjongTileTableLayout.drawnTilePlacement(
        controllerX = 10,
        controllerY = 64,
        controllerZ = -4,
        tableFacing = tableFacing,
        seatLayout = seatLayout,
        seatIndex = seatIndex,
        standingTileCount = standingTileCount,
        handShift = handShift,
    )

    /** 建立固定 controller 與可覆寫輸入的測試牌河 placement。 */
    private fun discardPlacement(
        seatLayout: MahjongTableSeatLayout,
        tableFacing: MahjongTableFacing = MahjongTableFacing.NORTH,
        seatIndex: Int = 0,
        discardIndex: Int = 0,
        isSidewaysMarked: Boolean = false,
        sidewaysMarkedDiscardIndex: Int? = null,
        wallRemaining: Boolean = true,
    ): MahjongTileWallPlacement = MahjongTileTableLayout.discardPlacement(
        controllerX = 10,
        controllerY = 64,
        controllerZ = -4,
        tableFacing = tableFacing,
        seatLayout = seatLayout,
        seatIndex = seatIndex,
        discardIndex = discardIndex,
        isSidewaysMarked = isSidewaysMarked,
        sidewaysMarkedDiscardIndex = sidewaysMarkedDiscardIndex,
        wallRemaining = wallRemaining,
    )

    /** showcase 錨點固定落在 controller 的桌面中心，不受牌張、座位或桌子朝向影響。 */
    @Test
    fun `showcase stage is anchored at tabletop center`() {
        val placement = MahjongTileTableLayout.showcaseStagePlacement(controllerX = 10, controllerY = 64, controllerZ = -4)

        assertEquals(CONTROLLER_CENTER_X, placement.x, ABSOLUTE_TOLERANCE)
        assertEquals(65.0, placement.y, ABSOLUTE_TOLERANCE)
        assertEquals(CONTROLLER_CENTER_Z, placement.z, ABSOLUTE_TOLERANCE)
        assertEquals(0.0f, placement.yaw)
    }

    /** 一張躺平牌的世界水平 footprint，用來檢查不同墩是否重疊。 */
    private data class Footprint(val minX: Double, val maxX: Double, val minZ: Double, val maxZ: Double)

    /** 依 yaw 判斷躺平牌的沿墩排列方向與垂直方向何者對應世界 X／Z，算出其 footprint。 */
    private fun MahjongTileWallPlacement.toFootprint(): Footprint {
        val alongHalf = MahjongTileDimensions.TILE_WIDTH / 2.0
        val perpendicularHalf = MahjongTileDimensions.TILE_HEIGHT / 2.0
        val normalizedYaw = ((yaw % FULL_YAW_DEGREES) + FULL_YAW_DEGREES) % FULL_YAW_DEGREES
        val (halfX, halfZ) = if (normalizedYaw == 0.0f || normalizedYaw == 180.0f) {
            alongHalf to perpendicularHalf
        } else {
            perpendicularHalf to alongHalf
        }
        return Footprint(minX = x - halfX, maxX = x + halfX, minZ = z - halfZ, maxZ = z + halfZ)
    }

    /** 只有 X 範圍與 Z 範圍都有正重疊時才視為碰撞；剛好貼齊的邊界不算碰撞。 */
    private fun Footprint.overlaps(other: Footprint): Boolean {
        val overlapX = minOf(maxX, other.maxX) - maxOf(minX, other.minX)
        val overlapZ = minOf(maxZ, other.maxZ) - maxOf(minZ, other.minZ)
        return overlapX > ABSOLUTE_TOLERANCE && overlapZ > ABSOLUTE_TOLERANCE
    }

    /** 座位正面朝向與該座位手牌、直立副露牌的 yaw 相同。 */
    @Test
    fun `seat yaw matches hand and upright meld tiles`() {
        MahjongTableFacing.entries.forEach { facing ->
            (0..3).forEach { seat ->
                val hand = MahjongTileTableLayout.handPlacement(
                    controllerX = 0,
                    controllerY = 64,
                    controllerZ = 0,
                    tableFacing = facing,
                    seatLayout = MahjongTableSeatLayout.FOUR_PLAYER,
                    seatIndex = seat,
                    handSize = 13,
                    tileIndex = 0,
                )
                val meld = MahjongTileTableLayout.meldPlacement(
                    controllerX = 0,
                    controllerY = 64,
                    controllerZ = 0,
                    tableFacing = facing,
                    seatLayout = MahjongTableSeatLayout.FOUR_PLAYER,
                    seatIndex = seat,
                    alongOffsetFromCorner = 0.1,
                    isSidewaysTile = false,
                )
                assertEquals(hand.yaw, MahjongTileTableLayout.seatYaw(facing, MahjongTableSeatLayout.FOUR_PLAYER, seat), "$facing seat $seat")
                assertEquals(meld.yaw, MahjongTileTableLayout.seatYaw(facing, MahjongTableSeatLayout.FOUR_PLAYER, seat), "$facing seat $seat")
            }
        }
    }

    /** 固定 controller 中心、每面墩數與浮點容許誤差。 */
    private companion object {
        const val STACKS_PER_SIDE: Int = 17
        const val SIDE_COUNT: Int = 4
        const val THREE_PLAYER_SIDE_COUNT: Int = 3
        const val THREE_PLAYER_STACKS_PER_SIDE: Int = 18
        const val HAND_SIZE: Int = 13
        const val CONTROLLER_CENTER_X: Double = 10.5
        const val CONTROLLER_CENTER_Z: Double = -3.5
        const val ABSOLUTE_TOLERANCE: Double = 1e-9
        const val FULL_YAW_DEGREES: Float = 360.0f
    }
}
