package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.seatIndexToTableSide

/** 局部水平向量；沿用 [MahjongTableSide]／[MahjongTableFacing] 兩段式旋轉合成，Y 軸不受水平旋轉影響。 */
private data class TileTableVector(val x: Double, val y: Double, val z: Double)

/**
 * 一張牌牆用牌的版本無關世界呈現位置。
 *
 * @property x 世界 X 座標，牌底面中心。
 * @property y 世界 Y 座標，牌底面中心。
 * @property z 世界 Z 座標，牌底面中心。
 * @property yaw 世界水平朝向角度。
 */
data class MahjongTileWallPlacement(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
)

/**
 * 把 [TileWallPosition] 這種與 Minecraft 座標無關的抽象牌牆結構位置，轉換成真實世界座標。
 *
 * 兩段式旋轉合成與 [com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout] 完全
 * 同一套慣例：先算出「以局部南側為基準」的向量，再依序旋轉到目標側面、旋轉到桌子世界朝向。
 * [TileWallPosition.side] 以莊家自身面為 0、逆時針遞增，跟 [seatIndexToTableSide] 採用的逆時針順序
 * （南→西→北→東）一致，因此只要把 [seatIndexToTableSide] 算出的莊家局部側面，依 [TileWallPosition.side]
 * 再旋轉相應步數，就能得到這張牌實際所在的局部側面。
 */
object MahjongTileTableLayout {
    /**
     * 依 controller 座標、桌子世界朝向、莊家座位與牌牆總墩數，算出單一 [TileWallPosition] 的世界座標。
     *
     * @param stacksPerSide 這副牌牆每面的總墩數（例如四人日麻固定 17），用來將牌墩對稱置中於側面。
     * @param isDeadWall 這張牌是否屬於王牌區——為 `true` 時額外把整條線沿排列方向、往開門缺口的方向
     * 滑動一點（見 [localWallVector]），跟活牌保持一點視覺距離。牌牆剛生成時所有牌（含王牌）都應該以
     * `false` 呼叫，維持一圈完整無縫的牌牆；等到骰子動畫播完、要把王牌區「移出」開門時，才對王牌區的
     * 牌改用 `true` 重新算一次座標並移動過去——這是刻意分兩階段呼叫的設計，不是可以合併成一次的參數。
     */
    fun wallPlacement(
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
        dealerSeatIndex: Int,
        stacksPerSide: Int,
        position: TileWallPosition,
        isDeadWall: Boolean = false,
    ): MahjongTileWallPlacement {
        require(stacksPerSide > 0) { "Stacks per side must be positive" }
        require(position.stack in 0 until stacksPerSide) {
            "Stack ${position.stack} out of range for $stacksPerSide stacks per side"
        }
        require(position.layer in 0..1) { "Layer ${position.layer} must be 0 or 1" }

        val physicalSide = advance(seatIndexToTableSide(dealerSeatIndex), position.side)
        val local = localWallVector(stacksPerSide, position.stack, position.layer, isDeadWall)
        val worldOffset = rotateForFacing(rotateForSide(local, physicalSide), tableFacing)
        return MahjongTileWallPlacement(
            x = controllerX + BLOCK_CENTER + worldOffset.x,
            y = controllerY + TABLETOP_HEIGHT + worldOffset.y,
            z = controllerZ + BLOCK_CENTER + worldOffset.z,
            yaw = (yawForSide(physicalSide) + yawForFacing(tableFacing)).mod(FULL_YAW_DEGREES),
        )
    }

    /**
     * 以局部南側玩家為基準的單張牌牆用牌位置：[TileWallPosition.layer] 沿 Y 軸以牌深堆疊，牌面朝下
     * 平放——躺平後，沿墩排列方向（局部 X 軸）的外觀寬度是 [MahjongTileDimensions.TILE_WIDTH]，垂直
     * 於側面方向（局部 Z 軸）的外觀寬度是 [MahjongTileDimensions.TILE_HEIGHT]（原本直立時的高度，
     * 躺平後變成水平方向的寬度）。
     *
     * 垂直於側面的偏移（`halfSpan`）刻意跟沿側面方向的半跨距使用同一個值——四面牌牆各自是一條長度
     * `stacksPerSide * stackStep` 的直線，若垂直偏移不等於這條線自己的半跨距，四面牌牆的直線就搆不到
     * 彼此，四個角會留下沒用到的空格（實際遊戲內驗證過的現象）。
     *
     * 但只讓兩個方向共用 `halfSpan` 還不夠：這樣四面牌牆的角落端點座標會完全重合，實際上牌本身有寬度
     * （`TILE_HEIGHT`），端點重合代表兩面牌牆在角落互相穿插重疊（同樣是實際遊戲內驗證過的現象）。
     * 因此再把整條線沿局部 X 軸整體平移 `TILE_HEIGHT / 2`——這個位移量與旋轉合成（[rotateForSide]／
     * [rotateForFacing]）自動套用到全部四面，讓四面牌牆形成像實體麻將桌那樣的風車形接角：每個角落都是
     * 一面牌牆的端邊貼齊另一面牌牆的側邊，不重疊。額外再加上 [MahjongTileDimensions.TILE_WIDTH] 的
     * [CORNER_GAP_RATIO] 倍，讓角落貼齊處留一點看得出來的縫隙，不是完全零間隙的貼合（遊戲內驗證後的
     * 觀感調整，不影響上面推導出的不重疊關係，只是把「剛好貼齊」再往外推一點；比例本身沒有幾何推導
     * 依據，純粹是視覺調校參數，用牌本身尺寸的比例表示比直接疊加 [MahjongTileDimensions.TILE_SMALL_PADDING]
     * 倍數更好預期調整後的視覺效果）。
     *
     * [isDeadWall] 為 `true` 時，額外把 `alongSide`（沿墩排列方向的位置）往「`stack` 遞增」的方向多推
     * [MahjongTileDimensions.TILE_WIDTH] 的 [DEAD_WALL_GAP_RATIO] 倍——`stack` 遞增的方向正是王牌區
     * 緊鄰開門缺口、之後會被摸走分配給玩家手牌的活牌墩所在方向（見
     * [com.doublemoon1119.mahjongcraft.logic.table.layout.FourSidedWallLayoutSupport] 的墩位排列
     * 邏輯：王牌從缺口本身往 `stack` 遞減方向連續佔用，活牌則從缺口另一側往 `stack` 遞增方向連續
     * 佔用，兩者在缺口處以遞增/遞減方向相鄰）。這是刻意選這個方向的位移，不是垂直推向桌子中心——
     * 活牌之後會被摸走清空這塊空間，王牌滑過去暫時會跟還沒摸走的活牌墩重疊一點點，等手牌分配這個
     * 切片完成後就會自然錯開，這次不需要另外處理這個過渡期的重疊。
     */
    private fun localWallVector(stacksPerSide: Int, stack: Int, layer: Int, isDeadWall: Boolean): TileTableVector {
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val halfSpan = stacksPerSide / 2.0 * stackStep
        val cornerInterlockShift = MahjongTileDimensions.TILE_HEIGHT / 2.0 +
            MahjongTileDimensions.TILE_WIDTH * CORNER_GAP_RATIO
        val deadWallOpeningShift = if (isDeadWall) MahjongTileDimensions.TILE_WIDTH * DEAD_WALL_GAP_RATIO else 0.0
        val alongSide = ((stacksPerSide - 1) / 2.0 - stack) * stackStep + cornerInterlockShift - deadWallOpeningShift
        val layerHeight = layer * MahjongTileDimensions.TILE_DEPTH +
            if (layer > 0) MahjongTileDimensions.TILE_SMALL_PADDING else 0.0
        return TileTableVector(x = alongSide, y = layerHeight, z = halfSpan)
    }

    /** 依南→西→北→東的固定逆時針順序，把 [side] 往同方向推進 [steps] 步。 */
    private fun advance(side: MahjongTableSide, steps: Int): MahjongTableSide = SIDE_ORDER[(SIDE_ORDER.indexOf(side) + steps).mod(SIDE_ORDER.size)]

    /** 將局部南側基準旋轉至指定側面。 */
    private fun rotateForSide(vector: TileTableVector, side: MahjongTableSide): TileTableVector = when (side) {
        MahjongTableSide.SOUTH -> vector
        MahjongTableSide.WEST -> TileTableVector(-vector.z, vector.y, vector.x)
        MahjongTableSide.NORTH -> TileTableVector(-vector.x, vector.y, -vector.z)
        MahjongTableSide.EAST -> TileTableVector(vector.z, vector.y, -vector.x)
    }

    /** 將朝北擺放桌子的局部向量旋轉至指定世界朝向。 */
    private fun rotateForFacing(vector: TileTableVector, facing: MahjongTableFacing): TileTableVector = when (facing) {
        MahjongTableFacing.NORTH -> vector
        MahjongTableFacing.EAST -> TileTableVector(-vector.z, vector.y, vector.x)
        MahjongTableFacing.SOUTH -> TileTableVector(-vector.x, vector.y, -vector.z)
        MahjongTableFacing.WEST -> TileTableVector(vector.z, vector.y, -vector.x)
    }

    /** 局部側面對應的基準 yaw，旋轉方向與 [rotateForSide] 的向量旋轉一致。 */
    private fun yawForSide(side: MahjongTableSide): Float = when (side) {
        MahjongTableSide.SOUTH -> 0.0f
        MahjongTableSide.WEST -> 90.0f
        MahjongTableSide.NORTH -> 180.0f
        MahjongTableSide.EAST -> 270.0f
    }

    /** 世界朝向對應的基準 yaw，旋轉方向與 [rotateForFacing] 的向量旋轉一致。 */
    private fun yawForFacing(facing: MahjongTableFacing): Float = when (facing) {
        MahjongTableFacing.NORTH -> 0.0f
        MahjongTableFacing.EAST -> 90.0f
        MahjongTableFacing.SOUTH -> 180.0f
        MahjongTableFacing.WEST -> 270.0f
    }

    /** 固定桌面幾何常數。 */
    private const val BLOCK_CENTER: Double = 0.5
    private const val TABLETOP_HEIGHT: Double = 1.0

    /**
     * 牌牆角落貼齊處縫隙相對 [MahjongTileDimensions.TILE_WIDTH] 的比例，遊戲內驗證後調整的觀感參數。
     * `internal` 而非 `private`：讓同模組的 [MahjongTileTableLayoutTest][com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayoutTest]
     * 能直接引用同一個數值驗證預期位移量，不需要在測試裡另外複製一份可能忘記同步的常數。
     */
    internal const val CORNER_GAP_RATIO: Double = 0.25

    /** 王牌區沿排列方向滑向開門缺口的距離相對 [MahjongTileDimensions.TILE_WIDTH] 的比例，使用者指定的觀感參數；`internal` 理由同 [CORNER_GAP_RATIO]。 */
    internal const val DEAD_WALL_GAP_RATIO: Double = 0.25
    private const val FULL_YAW_DEGREES: Float = 360.0f

    /** 南→西→北→東的固定逆時針側面順序，跟 [seatIndexToTableSide] 與 `TileWallPosition.side` 同一套慣例。 */
    private val SIDE_ORDER = listOf(MahjongTableSide.SOUTH, MahjongTableSide.WEST, MahjongTableSide.NORTH, MahjongTableSide.EAST)
}
