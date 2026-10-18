package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout.SEAT_DISTANCE
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import kotlin.math.atan2
import kotlin.uuid.Uuid

/**
 * 局部水平向量，沿用 [MahjongTableSide]／[MahjongTableFacing] 兩段式旋轉合成。
 *
 * @property x 局部 X 座標。
 * @property z 局部 Z 座標。
 */
private data class SeatVector(val x: Double, val z: Double)

/**
 * 麻將桌標準座位的座標計算。
 *
 * 整張桌子的實體結構固定是寬 3、深 3、高 2 的方塊範圍，controller 位於正中央下方；標準座位位於
 * 桌子側邊中點，離中心固定 [SEAT_DISTANCE] 格。座位 index 對應 [TableState.players] 固定不變的座位順序——
 * `TableState.advanceRound()` 只移交權威莊家、不重排 index；下一局自風由規則 policy 重新指派，因此這裡的座位 index
 * 必須跟規則層的玩家座位 index 一一對應，順序不能任意調整。
 *
 * 旋轉合成與 [MahjongTileTableLayout.handPlacement] 使用同一套桌子世界朝向慣例，確保玩家座位與同一
 * seatIndex 的手牌位置使用一致的方位。
 */
object MahjongSeatingTableLayout {
    /**
     * 依 controller 世界座標與桌子世界朝向，為指定座位布局建立席位請求，依座位 index 排列。
     *
     * @param layout 玩家人數對應的標準座位布局。
     * @param tableId 桌子識別碼。
     * @param dimensionId 桌子所在維度。
     * @param controllerX controller 的世界 X 座標。
     * @param controllerY controller 的世界 Y 座標。
     * @param controllerZ controller 的世界 Z 座標。
     * @param tableFacing 麻將桌的世界朝向。
     * @return 依座位 index 排列、帶標準座位的請求。
     */
    fun seatRequests(
        layout: MahjongTableSeatLayout,
        tableId: Uuid,
        dimensionId: String,
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
    ): List<SeatRequest> = seatPlacements(
        layout = layout,
        controllerX = controllerX,
        controllerY = controllerY,
        controllerZ = controllerZ,
        tableFacing = tableFacing,
    ).mapIndexed { seatIndex, seat ->
        SeatRequest.create(tableId = tableId, seatIndex = seatIndex, dimensionId = dimensionId, seat = seat)
    }

    /**
     * 依 controller 世界座標與桌子世界朝向，算出指定座位布局的腳底位置與面向桌子中心的朝向，依座位 index 排列。
     *
     * @param layout 玩家人數對應的標準座位布局。
     * @param controllerX controller 的世界 X 座標。
     * @param controllerY controller 的世界 Y 座標。
     * @param controllerZ controller 的世界 Z 座標。
     * @param tableFacing 麻將桌的世界朝向。
     * @return 依座位 index 排列的標準座位；腳底與 controller 同高。
     */
    fun seatPlacements(
        layout: MahjongTableSeatLayout,
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
    ): List<SeatGeometry> {
        val centerX = controllerX + BLOCK_CENTER
        val centerZ = controllerZ + BLOCK_CENTER
        return (0 until layout.playerCount).map { seatIndex ->
            val physicalSide = layout.sideOf(seatIndex)
            val worldOffset = rotateForFacing(rotateForSide(LOCAL_SOUTH_BASELINE, physicalSide), tableFacing)
            val x = centerX + worldOffset.x
            val z = centerZ + worldOffset.z
            SeatGeometry(
                x = x,
                y = controllerY.toDouble(),
                z = z,
                yaw = yawTowardCenter(x, z, centerX, centerZ),
            )
        }
    }

    /**
     * 依 Minecraft yaw 慣例（0 度朝南、逆時針遞增角度朝西）算出從座位面向桌子中心的水平朝向。
     *
     * @param seatX 座位世界 X 座標。
     * @param seatZ 座位世界 Z 座標。
     * @param centerX 桌子中心世界 X 座標。
     * @param centerZ 桌子中心世界 Z 座標。
     * @return 面向桌子中心的 Minecraft yaw 角度。
     */
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

    /**
     * 將局部南側基準旋轉至指定側面，與 [MahjongTileTableLayout] 同一套慣例。
     *
     * @param vector 待旋轉的局部向量。
     * @param side 目標桌面側邊。
     * @return 旋轉後的局部向量。
     */
    private fun rotateForSide(vector: SeatVector, side: MahjongTableSide): SeatVector = when (side) {
        MahjongTableSide.SOUTH -> vector
        MahjongTableSide.WEST -> SeatVector(-vector.z, vector.x)
        MahjongTableSide.NORTH -> SeatVector(-vector.x, -vector.z)
        MahjongTableSide.EAST -> SeatVector(vector.z, -vector.x)
    }

    /**
     * 將朝北擺放桌子的局部向量旋轉至指定世界朝向。
     *
     * @param vector 待旋轉的局部向量。
     * @param facing 桌子的世界朝向。
     * @return 旋轉後的世界向量。
     */
    private fun rotateForFacing(vector: SeatVector, facing: MahjongTableFacing): SeatVector = when (facing) {
        MahjongTableFacing.NORTH -> vector
        MahjongTableFacing.EAST -> SeatVector(-vector.z, vector.x)
        MahjongTableFacing.SOUTH -> SeatVector(-vector.x, -vector.z)
        MahjongTableFacing.WEST -> SeatVector(vector.z, -vector.x)
    }

    /** 讓玩家站在方塊正中央的置中偏移。 */
    private const val BLOCK_CENTER: Double = 0.5

    /** 座位離桌子中心的固定距離（格）。 */
    private const val SEAT_DISTANCE: Double = 2.0

    /** 局部南側基準座位向量，旋轉合成前的起點。 */
    private val LOCAL_SOUTH_BASELINE = SeatVector(x = 0.0, z = SEAT_DISTANCE)
}
