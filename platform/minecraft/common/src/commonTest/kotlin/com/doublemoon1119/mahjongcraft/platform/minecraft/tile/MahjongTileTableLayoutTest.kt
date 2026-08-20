package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDimensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

/** [MahjongTileTableLayout] 的墩位間距、四面旋轉、角落不重疊與輸入驗證測試。 */
class MahjongTileTableLayoutTest {
    /** 同一面內不同墩應等距排列，`stack` 0 在最大 X（該面玩家自己右手邊）、遞增時往負向移動；整條線
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

    /**
     * 四面牌牆的所有墩（含四個角落）躺平後的世界footprint 彼此都不該重疊——這正是實際在遊戲內發現過
     * 的 bug（角落偏移量只顧到端點座標重合，沒考慮牌本身的寬度）。footprint 沿墩排列方向的
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

    /**
     * 王牌區在牌牆剛生成時（`isDeadWall = false`）應跟活牌落在完全相同的座標，維持一圈無縫牌牆；
     * 只有事後標記為王牌（`isDeadWall = true`）才會沿排列方向、往 `stack` 遞增方向（開門缺口所在
     * 方向）滑動一點，垂直於側面的距離不變。
     */
    @Test
    fun `dead wall only slides toward the opening when explicitly marked`() {
        val position = TileWallPosition(side = 0, stack = 0, layer = 0)
        val asLiveWall = wallPlacement(position = position, isDeadWall = false)
        val asDeadWall = wallPlacement(position = position, isDeadWall = true)
        val expectedShift = MahjongTileDimensions.TILE_WIDTH * MahjongTileTableLayout.DEAD_WALL_GAP_RATIO

        assertEquals(asLiveWall.z, asDeadWall.z, ABSOLUTE_TOLERANCE)
        assertEquals(expectedShift, asLiveWall.x - asDeadWall.x, ABSOLUTE_TOLERANCE)
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
        val first = handPlacement(tileIndex = 0)
        val last = handPlacement(tileIndex = HAND_SIZE - 1)
        val middle = handPlacement(tileIndex = (HAND_SIZE - 1) / 2)
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING

        assertEquals((HAND_SIZE - 1) * stackStep, first.x - last.x, ABSOLUTE_TOLERANCE)
        assertEquals(CONTROLLER_CENTER_X, middle.x, ABSOLUTE_TOLERANCE)
        assertEquals(first.z, last.z, ABSOLUTE_TOLERANCE)
    }

    /** 手牌垂直於側面的距離應比牌牆更靠桌緣（比 `halfSpan` 更遠離桌子中心）。 */
    @Test
    fun `hand tiles sit closer to the table edge than the wall`() {
        val hand = handPlacement(tileIndex = 0)
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
        val seatZero = handPlacement(seatIndex = 0)
        val seatOne = handPlacement(seatIndex = 1)

        assertEquals(seatZero.z - CONTROLLER_CENTER_Z, seatOne.x - CONTROLLER_CENTER_X, ABSOLUTE_TOLERANCE)
        assertEquals(-(seatZero.x - CONTROLLER_CENTER_X), seatOne.z - CONTROLLER_CENTER_Z, ABSOLUTE_TOLERANCE)
    }

    /** 超出手牌張數範圍應直接拒絕。 */
    @Test
    fun `hand layout rejects out of range tile index`() {
        assertFailsWith<IllegalArgumentException> { handPlacement(tileIndex = HAND_SIZE) }
        assertFailsWith<IllegalArgumentException> { handPlacement(tileIndex = -1) }
    }

    /** 摸牌位應落在立牌列尾端（`tileIndex = 0`，玩家右手邊）外一段看得出來的縫隙，不是緊貼。 */
    @Test
    fun `drawn tile sits beyond the hand row edge with a visible gap`() {
        val firstStanding = handPlacement(handSize = HAND_SIZE, tileIndex = 0)
        val drawn = drawnTilePlacement(standingTileCount = HAND_SIZE)
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val gap = MahjongTileDimensions.TILE_WIDTH * MahjongTileTableLayout.DRAWN_TILE_GAP_RATIO

        assertEquals(stackStep + gap, drawn.x - firstStanding.x, ABSOLUTE_TOLERANCE)
        assertEquals(firstStanding.z, drawn.z, ABSOLUTE_TOLERANCE)
    }

    /** 摸牌位垂直於側面的距離應跟手牌共用同一個 `HAND_EDGE_OFFSET`。 */
    @Test
    fun `drawn tile shares the hand row's distance from center`() {
        val hand = handPlacement(tileIndex = 0)
        val drawn = drawnTilePlacement(standingTileCount = HAND_SIZE)

        assertEquals(hand.z, drawn.z, ABSOLUTE_TOLERANCE)
    }

    /** 牌河第 7 張（index 6）應換到下一排，跟前 6 張的垂直距離不同、第 0 欄的 X 相同。 */
    @Test
    fun `discard pile wraps to a new row after the row size`() {
        val firstOfRowOne = discardPlacement(discardIndex = 0)
        val lastOfRowOne = discardPlacement(discardIndex = MahjongTileTableLayout.DISCARD_TILES_PER_ROW - 1)
        val firstOfRowTwo = discardPlacement(discardIndex = MahjongTileTableLayout.DISCARD_TILES_PER_ROW)

        assertEquals(firstOfRowOne.z, lastOfRowOne.z, ABSOLUTE_TOLERANCE)
        assertEquals(firstOfRowOne.x, firstOfRowTwo.x, ABSOLUTE_TOLERANCE)
        assertNotEquals(firstOfRowOne.z, firstOfRowTwo.z)
    }

    /** 側身標記只改變 yaw（多轉 90 度），不改變座標。 */
    @Test
    fun `sideways marked discard tile only rotates yaw`() {
        val upright = discardPlacement(discardIndex = 0, isSidewaysMarked = false)
        val sideways = discardPlacement(discardIndex = 0, isSidewaysMarked = true)

        assertEquals(upright.x, sideways.x, ABSOLUTE_TOLERANCE)
        assertEquals(upright.y, sideways.y, ABSOLUTE_TOLERANCE)
        assertEquals(upright.z, sideways.z, ABSOLUTE_TOLERANCE)
        assertEquals(MahjongTileTableLayout.SIDEWAYS_YAW_OFFSET, ((sideways.yaw - upright.yaw) + FULL_YAW_DEGREES) % FULL_YAW_DEGREES, ABSOLUTE_TOLERANCE.toFloat())
    }

    /** 牌河整體應比牌牆更靠近桌子中心。 */
    @Test
    fun `discard pile sits closer to the table center than the wall`() {
        val discard = discardPlacement(discardIndex = 0)
        val wall = wallPlacement(position = TileWallPosition(side = 0, stack = 0, layer = 0))
        val centerZ = CONTROLLER_CENTER_Z

        assertEquals(true, (discard.z - centerZ) < (wall.z - centerZ), "Discard offset ${discard.z - centerZ} should be less than wall offset ${wall.z - centerZ}")
    }

    /** 負數 discardIndex 應直接拒絕。 */
    @Test
    fun `discard layout rejects negative index`() {
        assertFailsWith<IllegalArgumentException> { discardPlacement(discardIndex = -1) }
    }

    /**
     * 牌山還有剩餘牌時，第 [MahjongTileTableLayout.DISCARD_SAFE_ROWS] 排放滿後不應該新增第四排——
     * 應該固定停在最後一個安全排，沿著同一排方向繼續延伸（`z` 不變，`x` 持續往外）。
     */
    @Test
    fun `discard pile extends the last safe row instead of adding a fourth row while wall remains`() {
        val lastSafeRowStart = (MahjongTileTableLayout.DISCARD_SAFE_ROWS - 1) * MahjongTileTableLayout.DISCARD_TILES_PER_ROW
        val lastTileOfSafeRows = discardPlacement(discardIndex = lastSafeRowStart, wallRemaining = true)
        val overflow = discardPlacement(discardIndex = lastSafeRowStart + MahjongTileTableLayout.DISCARD_TILES_PER_ROW, wallRemaining = true)

        assertEquals(lastTileOfSafeRows.z, overflow.z, ABSOLUTE_TOLERANCE)
        assertNotEquals(lastTileOfSafeRows.x, overflow.x)
    }

    /** 牌山已經摸完時，第四排應該正常往桌緣方向新增，不再固定停在最後一個安全排。 */
    @Test
    fun `discard pile adds a fourth row once the wall is exhausted`() {
        val lastSafeRowStart = (MahjongTileTableLayout.DISCARD_SAFE_ROWS - 1) * MahjongTileTableLayout.DISCARD_TILES_PER_ROW
        val lastTileOfSafeRows = discardPlacement(discardIndex = lastSafeRowStart, wallRemaining = false)
        val fourthRowFirstTile = discardPlacement(discardIndex = lastSafeRowStart + MahjongTileTableLayout.DISCARD_TILES_PER_ROW, wallRemaining = false)

        assertNotEquals(lastTileOfSafeRows.z, fourthRowFirstTile.z)
    }

    /** 沒有任何副露時，副露區寬度應為零。 */
    @Test
    fun `meld area width is zero when there are no melds`() {
        assertEquals(0.0, MahjongTileTableLayout.meldAreaWidth(emptyList()), ABSOLUTE_TOLERANCE)
    }

    /** 沒有鳴取牌（暗槓）的一組副露，每張牌都是直立寬度，逐張累加加上間距即為總寬度。 */
    @Test
    fun `meld area width sums standing tile widths without a sideways tile`() {
        val meld = fakeMeld(type = MeldType.CLOSED_KAN, tileCount = 4, calledTileId = null)
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING

        assertEquals(4 * stackStep, MahjongTileTableLayout.meldAreaWidth(listOf(meld)), ABSOLUTE_TOLERANCE)
    }

    /** 有鳴取牌的一組副露，側身格位改用牌高當寬度，其餘格位仍是直立寬度。 */
    @Test
    fun `meld area width uses tile height for the sideways slot`() {
        val meld = fakeMeld(type = MeldType.PON, tileCount = 3, calledTileId = Uuid.random(), sourceDirection = RelativeDirection.Across)
        val standingStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val sidewaysStep = MahjongTileDimensions.TILE_HEIGHT + MahjongTileDimensions.TILE_SMALL_PADDING
        val expected = 2 * standingStep + sidewaysStep

        assertEquals(expected, MahjongTileTableLayout.meldAreaWidth(listOf(meld)), ABSOLUTE_TOLERANCE)
    }

    /** 加槓補上的第 4 張牌疊在側身牌旁邊，不佔用額外橫向格位，寬度應跟原本的碰（3 張）相同。 */
    @Test
    fun `meld area width does not count the added kan's fourth tile`() {
        val pon = fakeMeld(type = MeldType.PON, tileCount = 3, calledTileId = Uuid.random(), sourceDirection = RelativeDirection.Across)
        val addedKan = fakeMeld(type = MeldType.ADDED_KAN, tileCount = 4, calledTileId = pon.calledTileId, sourceDirection = RelativeDirection.Across)

        assertEquals(
            MahjongTileTableLayout.meldAreaWidth(listOf(pon)),
            MahjongTileTableLayout.meldAreaWidth(listOf(addedKan)),
            ABSOLUTE_TOLERANCE,
        )
    }

    /** 兩組副露之間應額外跳過一個 [MahjongTileTableLayout.MELD_GROUP_GAP]。 */
    @Test
    fun `meld area width adds a gap between groups`() {
        val meld = fakeMeld(type = MeldType.CLOSED_KAN, tileCount = 4, calledTileId = null)
        val oneGroup = MahjongTileTableLayout.meldAreaWidth(listOf(meld))
        val twoGroups = MahjongTileTableLayout.meldAreaWidth(listOf(meld, meld))

        assertEquals(2 * oneGroup + MahjongTileTableLayout.MELD_GROUP_GAP, twoGroups, ABSOLUTE_TOLERANCE)
    }

    /** 沒有積棒時，積棒區寬度應為零。 */
    @Test
    fun `stick area width is zero when there are no sticks`() {
        assertEquals(0.0, MahjongTileTableLayout.stickAreaWidth(0), ABSOLUTE_TOLERANCE)
    }

    /** 積棒支數不超過每排上限時，寬度隨支數線性增加。 */
    @Test
    fun `stick area width grows linearly up to one row`() {
        val stepWidth = MahjongScoringStickDimensions.STICK_DEPTH + MahjongTileDimensions.TILE_SMALL_PADDING

        assertEquals(stepWidth, MahjongTileTableLayout.stickAreaWidth(1), ABSOLUTE_TOLERANCE)
        assertEquals(3 * stepWidth, MahjongTileTableLayout.stickAreaWidth(3), ABSOLUTE_TOLERANCE)
        assertEquals(MahjongTileTableLayout.STICKS_PER_ROW * stepWidth, MahjongTileTableLayout.stickAreaWidth(MahjongTileTableLayout.STICKS_PER_ROW), ABSOLUTE_TOLERANCE)
    }

    /** 超過每排上限的積棒往 Y 軸疊層，不再增加寬度。 */
    @Test
    fun `stick area width stops growing once sticks stack into a new layer`() {
        val oneRowWidth = MahjongTileTableLayout.stickAreaWidth(MahjongTileTableLayout.STICKS_PER_ROW)
        val twoLayersWidth = MahjongTileTableLayout.stickAreaWidth(MahjongTileTableLayout.STICKS_PER_ROW + 2)

        assertEquals(oneRowWidth, twoLayersWidth, ABSOLUTE_TOLERANCE)
    }

    /** 手牌張數不多或副露／積棒沒有佔用空間時，不需要讓開。 */
    @Test
    fun `hand corner yield shift is zero when nothing needs to be avoided`() {
        assertEquals(0.0, MahjongTileTableLayout.handCornerYieldShift(0, 10.0), ABSOLUTE_TOLERANCE)
        assertEquals(0.0, MahjongTileTableLayout.handCornerYieldShift(HAND_SIZE, 0.0), ABSOLUTE_TOLERANCE)
        assertEquals(0.0, MahjongTileTableLayout.handCornerYieldShift(1, 0.5), ABSOLUTE_TOLERANCE)
    }

    /** 手牌外緣超過退讓後的邊界時，應回傳剛好讓外緣貼齊邊界所需的平移量。 */
    @Test
    fun `hand corner yield shift matches the overlap amount`() {
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val handRightEdge = (HAND_SIZE - 1) / 2.0 * stackStep + MahjongTileDimensions.TILE_WIDTH / 2.0
        val reservedCornerWidth = MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET
        val expectedShift = handRightEdge - (MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET - reservedCornerWidth - MahjongTileTableLayout.HAND_CORNER_GAP)

        assertEquals(expectedShift, MahjongTileTableLayout.handCornerYieldShift(HAND_SIZE, reservedCornerWidth), ABSOLUTE_TOLERANCE)
    }

    /**
     * 遊戲內實際回報過的 bug：摸牌位比立牌列尾端更靠近桌角一個 `stackStep` 加縫隙，若讓開偏移只看
     * 立牌列自己的外緣（不管 `hasDrawnTile`），算出來的偏移會不夠，摸牌位仍然撞進副露區。
     * `hasDrawnTile = true` 應該比 `false` 算出更大（或至少相等）的偏移，且摸牌位擺放後的外緣不得
     * 超過退讓後的邊界。
     */
    @Test
    fun `hand corner yield shift accounts for the drawn tile slot reaching further toward the corner`() {
        val reservedCornerWidth = MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET - 0.1
        val shiftWithoutDrawnTile = MahjongTileTableLayout.handCornerYieldShift(HAND_SIZE, reservedCornerWidth, hasDrawnTile = false)
        val shiftWithDrawnTile = MahjongTileTableLayout.handCornerYieldShift(HAND_SIZE, reservedCornerWidth, hasDrawnTile = true)

        assertEquals(true, shiftWithDrawnTile > shiftWithoutDrawnTile, "Expected $shiftWithDrawnTile to exceed $shiftWithoutDrawnTile")

        val drawn = drawnTilePlacement(standingTileCount = HAND_SIZE, cornerYieldShift = shiftWithDrawnTile)
        val drawnAlongSide = drawn.x - CONTROLLER_CENTER_X
        val drawnOuterEdge = drawnAlongSide + MahjongTileDimensions.TILE_WIDTH / 2.0
        val availableCornerBoundary = MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET - reservedCornerWidth - MahjongTileTableLayout.HAND_CORNER_GAP

        assertEquals(true, drawnOuterEdge <= availableCornerBoundary + ABSOLUTE_TOLERANCE, "Drawn tile outer edge $drawnOuterEdge should not exceed boundary $availableCornerBoundary")
    }

    /** [MahjongTileTableLayout.handPlacement] 帶上非零 `cornerYieldShift` 時，整排牌等量往玩家自己方向平移，牌與牌間距不變。 */
    @Test
    fun `hand placement shifts the whole row without compressing spacing`() {
        val shift = 0.4
        val unshifted = handPlacement(tileIndex = 0)
        val shiftedFirst = handPlacement(tileIndex = 0, cornerYieldShift = shift)
        val shiftedLast = handPlacement(tileIndex = HAND_SIZE - 1, cornerYieldShift = shift)
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING

        assertEquals(shift, unshifted.x - shiftedFirst.x, ABSOLUTE_TOLERANCE)
        assertEquals((HAND_SIZE - 1) * stackStep, shiftedFirst.x - shiftedLast.x, ABSOLUTE_TOLERANCE)
    }

    /** 積棒同一排內每支之間應等距排列，短邊（[MahjongScoringStickDimensions.STICK_DEPTH]）決定步距。 */
    @Test
    fun `stick placement spaces sticks evenly within a row`() {
        val first = stickPlacement(stickIndex = 0)
        val second = stickPlacement(stickIndex = 1)
        val stepWidth = MahjongScoringStickDimensions.STICK_DEPTH + MahjongTileDimensions.TILE_SMALL_PADDING

        assertEquals(stepWidth, first.x - second.x, ABSOLUTE_TOLERANCE)
        assertEquals(first.z, second.z, ABSOLUTE_TOLERANCE)
        assertEquals(first.y, second.y, ABSOLUTE_TOLERANCE)
    }

    /** 超過每排上限（[MahjongTileTableLayout.STICKS_PER_ROW]）的積棒應疊到下一層，水平座標回到第一欄、Y 軸升高一層。 */
    @Test
    fun `stick placement stacks onto a new layer past the row limit`() {
        val lastOfFirstRow = stickPlacement(stickIndex = MahjongTileTableLayout.STICKS_PER_ROW - 1)
        val firstOfFirstRow = stickPlacement(stickIndex = 0)
        val firstOfSecondRow = stickPlacement(stickIndex = MahjongTileTableLayout.STICKS_PER_ROW)
        val expectedLayerHeight = MahjongScoringStickDimensions.STICK_HEIGHT + MahjongTileDimensions.TILE_SMALL_PADDING

        assertEquals(firstOfFirstRow.x, firstOfSecondRow.x, ABSOLUTE_TOLERANCE)
        assertEquals(firstOfFirstRow.z, firstOfSecondRow.z, ABSOLUTE_TOLERANCE)
        assertEquals(expectedLayerHeight, firstOfSecondRow.y - firstOfFirstRow.y, ABSOLUTE_TOLERANCE)
        assertNotEquals(lastOfFirstRow.x, firstOfSecondRow.x)
    }

    /** 負數 stickIndex 應直接拒絕。 */
    @Test
    fun `stick placement rejects negative index`() {
        assertFailsWith<IllegalArgumentException> { stickPlacement(stickIndex = -1) }
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

    /** 建立測試用的最小副露資料，只填入寬度計算需要的欄位。 */
    private fun fakeMeld(
        type: MeldType,
        tileCount: Int,
        calledTileId: Uuid?,
        sourceDirection: RelativeDirection = RelativeDirection.Self,
    ): MahjongMeldTileGroup = MahjongMeldTileGroup(
        type = type,
        tileIds = List(tileCount) { Uuid.random() },
        calledTileId = calledTileId,
        sourceDirection = sourceDirection,
        allTilesFaceDown = false,
    )

    /** 建立固定 controller 與可覆寫輸入的測試 placement。 */
    private fun wallPlacement(
        tableFacing: MahjongTableFacing = MahjongTableFacing.NORTH,
        dealerSeatIndex: Int = 0,
        position: TileWallPosition = TileWallPosition(side = 0, stack = 0, layer = 0),
        isDeadWall: Boolean = false,
    ): MahjongTileWallPlacement = MahjongTileTableLayout.wallPlacement(
        controllerX = 10,
        controllerY = 64,
        controllerZ = -4,
        tableFacing = tableFacing,
        dealerSeatIndex = dealerSeatIndex,
        stacksPerSide = STACKS_PER_SIDE,
        position = position,
        isDeadWall = isDeadWall,
    )

    /** 建立固定 controller 與可覆寫輸入的測試手牌 placement。 */
    private fun handPlacement(
        tableFacing: MahjongTableFacing = MahjongTableFacing.NORTH,
        seatIndex: Int = 0,
        handSize: Int = HAND_SIZE,
        tileIndex: Int = 0,
        cornerYieldShift: Double = 0.0,
    ): MahjongTileWallPlacement = MahjongTileTableLayout.handPlacement(
        controllerX = 10,
        controllerY = 64,
        controllerZ = -4,
        tableFacing = tableFacing,
        seatIndex = seatIndex,
        handSize = handSize,
        tileIndex = tileIndex,
        cornerYieldShift = cornerYieldShift,
    )

    /** 建立固定 controller 與可覆寫輸入的測試積棒 placement。 */
    private fun stickPlacement(
        tableFacing: MahjongTableFacing = MahjongTableFacing.NORTH,
        seatIndex: Int = 0,
        stickIndex: Int = 0,
    ): MahjongTileWallPlacement = MahjongTileTableLayout.stickPlacement(
        controllerX = 10,
        controllerY = 64,
        controllerZ = -4,
        tableFacing = tableFacing,
        seatIndex = seatIndex,
        stickIndex = stickIndex,
    )

    /** 建立固定 controller 與可覆寫輸入的測試摸牌位 placement。 */
    private fun drawnTilePlacement(
        tableFacing: MahjongTableFacing = MahjongTableFacing.NORTH,
        seatIndex: Int = 0,
        standingTileCount: Int = HAND_SIZE,
        cornerYieldShift: Double = 0.0,
    ): MahjongTileWallPlacement = MahjongTileTableLayout.drawnTilePlacement(
        controllerX = 10,
        controllerY = 64,
        controllerZ = -4,
        tableFacing = tableFacing,
        seatIndex = seatIndex,
        standingTileCount = standingTileCount,
        cornerYieldShift = cornerYieldShift,
    )

    /** 建立固定 controller 與可覆寫輸入的測試牌河 placement。 */
    private fun discardPlacement(
        tableFacing: MahjongTableFacing = MahjongTableFacing.NORTH,
        seatIndex: Int = 0,
        discardIndex: Int = 0,
        isSidewaysMarked: Boolean = false,
        wallRemaining: Boolean = true,
    ): MahjongTileWallPlacement = MahjongTileTableLayout.discardPlacement(
        controllerX = 10,
        controllerY = 64,
        controllerZ = -4,
        tableFacing = tableFacing,
        seatIndex = seatIndex,
        discardIndex = discardIndex,
        isSidewaysMarked = isSidewaysMarked,
        wallRemaining = wallRemaining,
    )

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

    /** 固定 controller 中心、每面墩數與浮點容許誤差。 */
    private companion object {
        const val STACKS_PER_SIDE: Int = 17
        const val SIDE_COUNT: Int = 4
        const val HAND_SIZE: Int = 13
        const val CONTROLLER_CENTER_X: Double = 10.5
        const val CONTROLLER_CENTER_Z: Double = -3.5
        const val ABSOLUTE_TOLERANCE: Double = 1e-9
        const val FULL_YAW_DEGREES: Float = 360.0f
    }
}
