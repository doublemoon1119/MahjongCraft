package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.seatIndexToTableSide
import kotlin.math.atan2

/** 局部水平向量，沿用 [MahjongTableSide]／[MahjongTableFacing] 兩段式旋轉合成。 */
private data class SeatVector(val x: Double, val z: Double)

/**
 * 麻將桌單一座位的世界座標與朝向。
 *
 * @property x 置中後的世界 X 座標。
 * @property y 世界 Y 座標，與 controller 同高。
 * @property z 置中後的世界 Z 座標。
 * @property yaw 面向桌子中心（controller）的水平朝向角度。
 */
data class MahjongSeatPlacement(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
)

/**
 * 麻將桌四個固定座位的座標計算。
 *
 * 整張桌子的實體結構固定是寬 3、深 3、高 2 的方塊範圍，controller 位於正中央下方；四個座位因此位於
 * 桌子四個側邊中點，離中心固定 [SEAT_DISTANCE] 格。座位依 index 0～3 順時針排列（[seatIndexToTableSide]
 * 的方向，跟牌牆自身組裝用的逆時針 `SIDE_ORDER` 是刻意不同的兩套獨立方向，見該函式 KDoc）——這是
 * 為了讓回合順序中的下一位玩家（`TableState.getNextPlayer()`，下家）physically 站在目前玩家的右手邊，
 * 符合真實麻將慣例。座位 index 對應
 * [com.doublemoon1119.mahjongcraft.logic.table.TableState.players] 固定不變的座位順序——
 * `TableState.advanceRound()` 只轉動每個 index 位置玩家的自風，不重排 index，因此這裡的座位 index
 * 必須跟規則層的玩家座位 index 一一對應，順序不能任意調整。
 *
 * 旋轉合成與 [com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout.handPlacement]
 * 完全同一套慣例（同樣以 [seatIndexToTableSide] 把座位 index 換算局部側面，不經過莊家相對旋轉，只套用
 * 桌子世界朝向）——先前這裡漏了套用 [MahjongTableFacing] 這一段，導致桌子朝向不是預設值時，玩家實際
 * 站立的座位跟同一 seatIndex 手牌呈現的位置對不上（曾實際造成手牌出現在對面玩家座位的錯位）；修好後
 * 兩套系統共用同一個旋轉來源，不會再各自累積出不一致的方位。
 */
object MahjongSeatingTableLayout {
    /** 依 controller 世界座標與桌子世界朝向，算出四個座位的座標與面向桌子中心的朝向，依 index 逆時針排列。 */
    fun seatPlacements(controllerX: Int, controllerY: Int, controllerZ: Int, tableFacing: MahjongTableFacing): List<MahjongSeatPlacement> {
        val centerX = controllerX + BLOCK_CENTER
        val centerZ = controllerZ + BLOCK_CENTER
        return (0 until SEAT_COUNT).map { seatIndex ->
            val physicalSide = seatIndexToTableSide(seatIndex)
            val worldOffset = rotateForFacing(rotateForSide(LOCAL_SOUTH_BASELINE, physicalSide), tableFacing)
            val x = centerX + worldOffset.x
            val z = centerZ + worldOffset.z
            MahjongSeatPlacement(
                x = x,
                y = controllerY.toDouble(),
                z = z,
                yaw = yawTowardCenter(x, z, centerX, centerZ),
            )
        }
    }

    /** 依 Minecraft yaw 慣例（0 度朝南、逆時針遞增角度朝西）算出從座位面向桌子中心的水平朝向。 */
    private fun yawTowardCenter(
        seatX: Double,
        seatZ: Double,
        centerX: Double,
        centerZ: Double,
    ): Float {
        val dx = centerX - seatX
        val dz = centerZ - seatZ
        return Math.toDegrees(atan2(-dx, dz)).toFloat()
    }

    /** 將局部南側基準旋轉至指定側面，與 [com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout] 同一套慣例。 */
    private fun rotateForSide(vector: SeatVector, side: MahjongTableSide): SeatVector = when (side) {
        MahjongTableSide.SOUTH -> vector
        MahjongTableSide.WEST -> SeatVector(-vector.z, vector.x)
        MahjongTableSide.NORTH -> SeatVector(-vector.x, -vector.z)
        MahjongTableSide.EAST -> SeatVector(vector.z, -vector.x)
    }

    /** 將朝北擺放桌子的局部向量旋轉至指定世界朝向。 */
    private fun rotateForFacing(vector: SeatVector, facing: MahjongTableFacing): SeatVector = when (facing) {
        MahjongTableFacing.NORTH -> vector
        MahjongTableFacing.EAST -> SeatVector(-vector.z, vector.x)
        MahjongTableFacing.SOUTH -> SeatVector(-vector.x, -vector.z)
        MahjongTableFacing.WEST -> SeatVector(vector.z, -vector.x)
    }

    /** 讓玩家站在方塊正中央的置中偏移。 */
    private const val BLOCK_CENTER: Double = 0.5

    /** 座位離桌子中心的固定距離（格），沿用先前 `SEAT_OFFSETS` 的既有數值。 */
    private const val SEAT_DISTANCE: Double = 2.0

    /** 座位總數。 */
    private const val SEAT_COUNT: Int = 4

    /** 局部南側基準座位向量，旋轉合成前的起點。 */
    private val LOCAL_SOUTH_BASELINE = SeatVector(x = 0.0, z = SEAT_DISTANCE)
}
