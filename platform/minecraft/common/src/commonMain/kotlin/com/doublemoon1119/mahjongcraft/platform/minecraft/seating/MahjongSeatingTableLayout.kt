package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import kotlin.math.atan2

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
 * 桌子四個側邊中點——每次只有 X 或 Z 其中一軸偏移 controller ±2、另一軸維持 0，不需要另外對照桌子
 * 模型量測，兩款桌型（木製／混凝土）共用同一組計算。座位依 index 0～3 逆時針排列，對應
 * [com.doublemoon1119.mahjongcraft.logic.table.TableState.players] 固定不變的座位順序——
 * `TableState.advanceRound()` 只轉動每個 index 位置玩家的自風，不重排 index，因此這裡的座位 index
 * 必須跟規則層的玩家座位 index 一一對應，順序不能任意調整。
 */
object MahjongSeatingTableLayout {
    /** 依 controller 世界座標算出四個座位的座標與面向桌子中心的朝向，依 index 逆時針排列。 */
    fun seatPlacements(controllerX: Int, controllerY: Int, controllerZ: Int): List<MahjongSeatPlacement> {
        val centerX = controllerX + BLOCK_CENTER
        val centerZ = controllerZ + BLOCK_CENTER
        return SEAT_OFFSETS.map { (dx, dz) ->
            val x = controllerX + dx + BLOCK_CENTER
            val z = controllerZ + dz + BLOCK_CENTER
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

    /** 讓玩家站在方塊正中央的置中偏移。 */
    private const val BLOCK_CENTER: Double = 0.5

    /** 四個座位相對 controller 的 X／Z 偏移（桌子四個側邊中點，每次只有一軸偏移），依 index 逆時針排列。 */
    private val SEAT_OFFSETS: List<Pair<Int, Int>> = listOf(
        2 to 0,
        0 to 2,
        -2 to 0,
        0 to -2,
    )
}
