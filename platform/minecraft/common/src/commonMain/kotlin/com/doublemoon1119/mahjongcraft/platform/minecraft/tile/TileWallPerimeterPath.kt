package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.math.abs
import kotlin.math.hypot

/**
 * 產生跨越相對兩面牌牆時沿桌面外緣繞行的路徑。
 *
 * 這個 helper 只接受兩端水平位置位於桌心相對兩側，且朝向相差 180 度的情況。
 * 路徑不會穿越桌面中央；南北兩端會在東側或西側繞行，東西兩端會在北側或南側繞行。
 */
internal object TileWallPerimeterPath {
    /**
     * 以兩個桌角與起訖位置建立相對牆面的外緣折線。
     *
     * @param start 路徑起點。
     * @param end 路徑終點。
     * @param centerX 桌面中心的 X 座標。
     * @param centerZ 桌面中心的 Z 座標。
     * @return 包含起點、兩個外側角點與終點的路徑；起訖物件會原樣保留。
     */
    fun oppositeSidePath(
        start: MahjongTileWallPlacement,
        end: MahjongTileWallPlacement,
        centerX: Double,
        centerZ: Double,
    ): List<MahjongTileWallPlacement> {
        require(abs(shortestYawDeltaDegrees(start.yaw, end.yaw)) in (HALF_TURN - YAW_EPSILON)..(HALF_TURN + YAW_EPSILON)) {
            "Opposite-side path requires a 180-degree yaw change"
        }

        val startX = start.x - centerX
        val startZ = start.z - centerZ
        val endX = end.x - centerX
        val endZ = end.z - centerZ
        val radius = maxOf(abs(startX), abs(startZ), abs(endX), abs(endZ))
        val northSouth = abs(startZ) + abs(endZ) >= abs(startX) + abs(endX)
        val candidates = if (northSouth) {
            listOf(
                route(
                    start,
                    end,
                    centerX + radius,
                    centerZ + sign(startZ) * radius,
                    centerZ + sign(endZ) * radius,
                ),
                route(
                    start,
                    end,
                    centerX - radius,
                    centerZ + sign(startZ) * radius,
                    centerZ + sign(endZ) * radius,
                ),
            )
        } else {
            listOf(
                route(
                    start,
                    end,
                    centerX + sign(startX) * radius,
                    centerZ + radius,
                    centerX + sign(endX) * radius,
                    isNorthSouth = false,
                ),
                route(
                    start,
                    end,
                    centerX + sign(startX) * radius,
                    centerZ - radius,
                    centerX + sign(endX) * radius,
                    isNorthSouth = false,
                ),
            )
        }
        return candidates.minBy { pathLength(it) }
    }

    /**
     * 依指定的外側方向組成起點、第一個角點、第二個角點與終點。
     *
     * @param start 路徑起點。
     * @param end 路徑終點。
     * @param firstCornerX 第一個角點的 X 座標。
     * @param firstCornerZ 第一個角點的 Z 座標。
     * @param secondCornerXOrZ 南北路徑時第二個角點的 Z 座標；東西路徑時的 X 座標。
     * @param isNorthSouth 是否為南北兩端繞東西側的路徑。
     * @return 包含起訖點和兩個外側角點的路徑。
     */
    private fun route(
        start: MahjongTileWallPlacement,
        end: MahjongTileWallPlacement,
        firstCornerX: Double,
        firstCornerZ: Double,
        secondCornerXOrZ: Double,
        isNorthSouth: Boolean = true,
    ): List<MahjongTileWallPlacement> {
        val first = point(firstCornerX, firstCornerZ, start.yaw, start, end, 1)
        val second = if (isNorthSouth) {
            point(firstCornerX, secondCornerXOrZ, start.yaw, start, end, 2)
        } else {
            point(secondCornerXOrZ, firstCornerZ, start.yaw, start, end, 2)
        }
        return listOf(start, first, second, end)
    }

    /**
     * 建立一個外側角點，並依路徑比例插入朝向。
     *
     * @param x 角點的世界 X 座標。
     * @param z 角點的世界 Z 座標。
     * @param startYaw 起點 yaw。
     * @param start 原始路徑起點。
     * @param end 原始路徑終點。
     * @param index 角點在兩個角點中的順序。
     * @return 帶有插值 yaw 的角點 placement。
     */
    private fun point(
        x: Double,
        z: Double,
        startYaw: Float,
        start: MahjongTileWallPlacement,
        end: MahjongTileWallPlacement,
        index: Int,
    ): MahjongTileWallPlacement {
        val progress = when (index) {
            1 -> 0.25f
            2 -> 0.75f
            else -> 1.0f
        }
        return MahjongTileWallPlacement(
            x = x,
            y = start.y,
            z = z,
            yaw = (startYaw + shortestYawDeltaDegrees(startYaw, end.yaw) * progress).mod(FULL_TURN),
        )
    }

    /** 計算折線各段的水平總長度。 */
    private fun pathLength(path: List<MahjongTileWallPlacement>): Double = path.zipWithNext().sumOf {
        hypot(it.second.x - it.first.x, it.second.z - it.first.z)
    }

    /** 將座標相對桌心的位置轉成外側方向；正值與零值使用正側。 */
    private fun sign(value: Double): Double = when {
        value < 0.0 -> -1.0
        else -> 1.0
    }

    /** yaw 相反時的理論角度差。 */
    private const val HALF_TURN = 180.0f

    /** yaw 標準化使用的一整圈角度。 */
    private const val FULL_TURN = 360.0f

    /** 判斷 yaw 是否足以視為正好相差 180 度的容許誤差。 */
    private const val YAW_EPSILON = 0.001f
}
