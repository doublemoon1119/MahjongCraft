package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 把兩個規則 placement 之間的移動規劃為不穿越桌面的版本無關路徑。
 *
 * 此類別只處理幾何，不讀桌況、不安排絕對時間，也不建立 entity 動畫佇列。
 */
object TileWallMotionPathPlanner {
    /**
     * 依 [context] 規劃由 [start] 移至 [end] 的路徑。
     *
     * @param context 此桌的幾何投影資料。
     * @param start 路徑起點。
     * @param end 路徑終點。
     * @return 安全路徑，或拒絕規劃的原因。
     */
    fun plan(
        context: MahjongTileWallProjectionContext,
        start: TileWallPlacement,
        end: TileWallPlacement,
    ): TileWallMotionPathDecision {
        val startWorld = context.project(start)
        val endWorld = context.project(end)
        val horizontalDistance = hypot(endWorld.x - startWorld.x, endWorld.z - startWorld.z)
        val verticalDistance = abs(endWorld.y - startWorld.y)
        if (horizontalDistance > POSITION_EPSILON && verticalDistance > POSITION_EPSILON) {
            return TileWallMotionPathDecision.Rejected(TileWallMotionPathRejection.MIXED_HORIZONTAL_AND_VERTICAL)
        }
        if (horizontalDistance <= POSITION_EPSILON) {
            return TileWallMotionPathDecision.Completed(listOf(segment(startWorld, endWorld)))
        }

        val startCoordinate = circularCoordinate(start, context.stacksPerSide)
        val endCoordinate = unwrapNear(startCoordinate, circularCoordinate(end, context.stacksPerSide), context.stacksPerSide * context.sideCount)
        val traversedCornerCells = traversedCornerCells(startCoordinate, endCoordinate, context.stacksPerSide)
        if (traversedCornerCells.isEmpty()) {
            return TileWallMotionPathDecision.Completed(listOf(segment(startWorld, endWorld)))
        }
        if (traversedCornerCells.size != 1) {
            return TileWallMotionPathDecision.Rejected(TileWallMotionPathRejection.NON_ADJACENT_SIDES)
        }
        if (start.orientation == end.orientation &&
            abs(shortestYawDeltaDegrees(startWorld.yaw, endWorld.yaw)) in (HALF_TURN - YAW_EPSILON)..(HALF_TURN + YAW_EPSILON)
        ) {
            val perimeterPath = TileWallPerimeterPath.oppositeSidePath(
                start = startWorld,
                end = endWorld,
                centerX = context.controllerX + BLOCK_CENTER,
                centerZ = context.controllerZ + BLOCK_CENTER,
            )
            return TileWallMotionPathDecision.Completed(roundedPathSegments(perimeterPath))
        }
        return TileWallMotionPathDecision.Completed(roundedCornerSegments(context, startWorld, endWorld))
    }

    /**
     * 以直線入口、四分之一圓近似及直線出口建立相鄰牆面的貼桌路徑。
     *
     * @param context 桌面幾何投影資料。
     * @param start 路徑起點的世界座標與朝向。
     * @param end 路徑終點的世界座標與朝向。
     * @return 依序連接入口、圓角近似點及出口的路徑段。
     */
    private fun roundedCornerSegments(
        context: MahjongTileWallProjectionContext,
        start: MahjongTileWallPlacement,
        end: MahjongTileWallPlacement,
    ): List<TileWallMotionSegment> {
        val centerX = context.controllerX + BLOCK_CENTER
        val centerZ = context.controllerZ + BLOCK_CENTER
        val firstCandidate = HorizontalPoint(end.x, start.z)
        val secondCandidate = HorizontalPoint(start.x, end.z)
        val corner = maxOf(firstCandidate, secondCandidate, compareBy { it.distanceSquaredFrom(centerX, centerZ) })
        return roundedPathSegments(listOf(start, start.withHorizontal(corner, start.yaw), end))
    }

    /**
     * 沿 [vertices] 連成的折線建立路徑，每個中間轉折點都改成外圓角。
     *
     * - 每個轉折點前後各取最多 [CORNER_RADIUS] 的距離作為圓角的入口與出口，中間以二次曲線近似。
     *   兩個轉折點之間的直線不足兩個半徑時，兩邊各取一半，圓角不重疊。
     * - 直線段維持固定朝向，轉向只在圓角內完成：第一段沿用起點朝向，最後一段沿用終點朝向，
     *   中間的直線段取兩端轉折點朝向的中間值。
     *
     * @param vertices 起點、各轉折點與終點。轉折點的朝向是轉過該角一半時的朝向。
     * @return 依序連接直線段與圓角近似點的路徑段。
     */
    private fun roundedPathSegments(vertices: List<MahjongTileWallPlacement>): List<TileWallMotionSegment> {
        val start = vertices.first()
        val end = vertices.last()
        val legYaws = vertices.indices.toList().dropLast(1).map { leg ->
            when (leg) {
                0 -> start.yaw
                vertices.lastIndex - 1 -> end.yaw
                else -> vertices[leg].yaw + shortestYawDeltaDegrees(vertices[leg].yaw, vertices[leg + 1].yaw) / 2.0f
            }
        }
        val points = buildList {
            add(start)
            for (index in 1 until vertices.lastIndex) {
                val corner = HorizontalPoint(vertices[index].x, vertices[index].z)
                val entry = pointToward(corner, vertices[index - 1].horizontal(), cornerRadius(vertices, leg = index - 1))
                val exit = pointToward(corner, vertices[index + 1].horizontal(), cornerRadius(vertices, leg = index))
                val yawIn = legYaws[index - 1]
                val yawOut = legYaws[index]
                if (hypot(entry.x - last().x, entry.z - last().z) > POSITION_EPSILON) add(start.withHorizontal(entry, yawIn))
                for (step in 1..CORNER_SUBDIVISIONS) {
                    val progress = step.toDouble() / CORNER_SUBDIVISIONS
                    val yaw = yawIn + shortestYawDeltaDegrees(yawIn, yawOut) * progress.toFloat()
                    add(start.withHorizontal(quadraticBezier(entry, corner, exit, progress), yaw))
                }
            }
            if (horizontalDistance(last(), end) > POSITION_EPSILON || last().yaw != end.yaw) add(end)
        }
        return points.zipWithNext(::segment)
    }

    /**
     * 一段直線上圓角可用的半徑。兩端都是轉折點時最多用到一半長度，避免前後兩個圓角重疊。
     *
     * @param vertices 起點、各轉折點與終點。
     * @param leg 直線段的序號，從 `vertices[leg]` 到 `vertices[leg + 1]`。
     * @return 這段直線上每個圓角可用的半徑。
     */
    private fun cornerRadius(vertices: List<MahjongTileWallPlacement>, leg: Int): Double {
        val betweenCorners = leg > 0 && leg + 1 < vertices.lastIndex
        return if (betweenCorners) minOf(CORNER_RADIUS, horizontalDistance(vertices[leg], vertices[leg + 1]) / 2.0) else CORNER_RADIUS
    }

    /**
     * placement 的水平座標。
     *
     * @return 忽略高度與朝向的水平座標。
     */
    private fun MahjongTileWallPlacement.horizontal(): HorizontalPoint = HorizontalPoint(x, z)

    /**
     * 建立一段按世界距離換算時長的路徑。
     *
     * @param start 路徑段起點。
     * @param end 路徑段終點。
     * @return 含換算播放時長的路徑段。
     */
    private fun segment(start: MahjongTileWallPlacement, end: MahjongTileWallPlacement): TileWallMotionSegment {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        val duration = ceil(distance * TICKS_PER_BLOCK).toInt().coerceAtLeast(MIN_SEGMENT_TICKS)
        return TileWallMotionSegment(start, end, duration)
    }

    /**
     * 回傳 [from] 朝 [toward] 前進最多 [distance] 的點。
     *
     * @param from 移動起點。
     * @param toward 移動方向上的目標點。
     * @param distance 沿方向前進的最大距離。
     * @return 未超過目標點的水平座標。
     */
    private fun pointToward(from: HorizontalPoint, toward: HorizontalPoint, distance: Double): HorizontalPoint {
        val dx = toward.x - from.x
        val dz = toward.z - from.z
        val length = hypot(dx, dz)
        if (length <= distance) return toward
        return HorizontalPoint(from.x + dx / length * distance, from.z + dz / length * distance)
    }

    /**
     * 計算二次貝茲曲線上的一點。
     *
     * @param start 曲線起點。
     * @param control 曲線控制點。
     * @param end 曲線終點。
     * @param progress 沿曲線的位置比例。
     * @return 指定比例上的水平座標。
     */
    private fun quadraticBezier(
        start: HorizontalPoint,
        control: HorizontalPoint,
        end: HorizontalPoint,
        progress: Double,
    ): HorizontalPoint {
        val remaining = 1.0 - progress
        return HorizontalPoint(
            x = remaining * remaining * start.x + 2.0 * remaining * progress * control.x + progress * progress * end.x,
            z = remaining * remaining * start.z + 2.0 * remaining * progress * control.z + progress * progress * end.z,
        )
    }

    /**
     * 將 placement 轉成包含小數沿牆位移的環狀墩座標。
     *
     * @param placement 要換算的牌牆位置。
     * @param stacksPerSide 每一側的墩數。
     * @return 沿牌牆一圈累計的墩座標。
     */
    private fun circularCoordinate(placement: TileWallPlacement, stacksPerSide: Int): Double = placement.position.side *
        stacksPerSide + placement.position.stack + placement.offset.alongWallStacks

    /**
     * 將 [coordinate] 展開到最接近 [reference] 的同一圈表示。[stackCount] 是整圈牌牆的墩數。
     *
     * @param reference 用來選擇同圈表示的參考座標。
     * @param coordinate 原始環狀座標。
     * @param stackCount 整圈牌牆的墩數。
     * @return 與參考座標距離最近的等價座標。
     */
    private fun unwrapNear(reference: Double, coordinate: Double, stackCount: Int): Double {
        val circumference = stackCount.toDouble()
        var result = coordinate
        while (result - reference > circumference / 2.0) result -= circumference
        while (result - reference < -circumference / 2.0) result += circumference
        return result
    }

    /**
     * 列出起訖座標經過的牆角格。每一面最後一墩到下一面第一墩之間的完整單位區間視為牆角。
     *
     * @param start 起始環狀座標。
     * @param end 結束環狀座標。
     * @param stacksPerSide 每一側的墩數。
     * @return 起訖範圍內代表牆角的格子索引。
     */
    private fun traversedCornerCells(start: Double, end: Double, stacksPerSide: Int): Set<Int> {
        val lower = floor(minOf(start, end)).toInt()
        val upper = floor(maxOf(start, end) - POSITION_EPSILON).toInt()
        if (upper < lower) return emptySet()
        return (lower..upper).filterTo(mutableSetOf()) { coordinate ->
            coordinate.mod(stacksPerSide) == stacksPerSide - 1
        }
    }

    /**
     * 只替換 placement 的水平位置與 yaw。
     *
     * @param point 新的水平座標。
     * @param targetYaw 新的 yaw 角度。
     * @return 保留高度並套用新水平位置與標準化 yaw 的 placement。
     */
    private fun MahjongTileWallPlacement.withHorizontal(point: HorizontalPoint, targetYaw: Float): MahjongTileWallPlacement = copy(
        x = point.x,
        z = point.z,
        yaw = targetYaw.mod(FULL_TURN_DEGREES),
    )

    /**
     * 兩個世界 placement 的水平距離。
     *
     * @param first 第一個世界座標。
     * @param second 第二個世界座標。
     * @return 忽略高度後的直線距離。
     */
    private fun horizontalDistance(first: MahjongTileWallPlacement, second: MahjongTileWallPlacement): Double = hypot(
        x = second.x - first.x,
        y = second.z - first.z,
    )

    /** 控制方塊中心的座標偏移。 */
    private const val BLOCK_CENTER = 0.5

    /** 圓角入口及出口距離牆面交點的世界距離。 */
    private const val CORNER_RADIUS = 0.35

    /** 二次曲線使用的固定折線細分數。 */
    private const val CORNER_SUBDIVISIONS = 3

    /** 每移動一個世界方塊所需的 tick 數。 */
    private const val TICKS_PER_BLOCK = 16.0

    /** 非零路徑段的最短播放時間，確保短距離整理仍能看清移動過程。 */
    private const val MIN_SEGMENT_TICKS = 3

    /** 判斷世界位置相同時使用的容差。 */
    private const val POSITION_EPSILON = 1e-9

    /** 一整圈的角度。 */
    private const val FULL_TURN_DEGREES = 360.0f

    /** yaw 相反時的理論角度差。 */
    private const val HALF_TURN = 180.0f

    /** 判斷起訖 yaw 是否足以視為相差 180 度的容許誤差。 */
    private const val YAW_EPSILON = 0.001f
}

/** 路徑規劃內部使用的水平座標。 */
private data class HorizontalPoint(val x: Double, val z: Double) {
    /**
     * 與指定桌心的平方距離。
     *
     * @param centerX 桌心的 X 座標。
     * @param centerZ 桌心的 Z 座標。
     * @return 此點到桌心的水平平方距離。
     */
    fun distanceSquaredFrom(centerX: Double, centerZ: Double): Double {
        val dx = x - centerX
        val dz = z - centerZ
        return dx * dx + dz * dz
    }
}
