package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sqrt

/** 將抽象牌牆 placement 投影到特定桌子的必要資料。 */
data class MahjongTileWallProjectionContext(
    /** 控制方塊 X 座標。 */
    val controllerX: Int,
    /** 控制方塊 Y 座標。 */
    val controllerY: Int,
    /** 控制方塊 Z 座標。 */
    val controllerZ: Int,
    /** 桌子在世界中的朝向。 */
    val tableFacing: MahjongTableFacing,
    /** 本局莊家座位索引。 */
    val dealerSeatIndex: Int,
    /** 牌牆每面的墩數。 */
    val stacksPerSide: Int,
) {
    init {
        require(stacksPerSide > 0) { "Stacks per side must be positive" }
    }

    /** 將 [placement] 投影成此桌的世界座標與朝向。 */
    fun project(placement: TileWallPlacement): MahjongTileWallPlacement = MahjongTileTableLayout.wallPlacement(
        controllerX = controllerX,
        controllerY = controllerY,
        controllerZ = controllerZ,
        tableFacing = tableFacing,
        dealerSeatIndex = dealerSeatIndex,
        stacksPerSide = stacksPerSide,
        placement = placement,
    )
}

/** 一段由絕對起點移至絕對終點的確定性線性運動。 */
data class TileWallMotionSegment(
    /** 本段畫面起點。 */
    val start: MahjongTileWallPlacement,
    /** 本段真實終點。 */
    val end: MahjongTileWallPlacement,
    /** 本段播放時間。 */
    val durationTicks: Int,
) {
    init {
        require(durationTicks > 0) { "Wall motion segment duration must be positive" }
    }
}

/** 牌牆路徑規劃的強型別結果。 */
sealed interface TileWallMotionPathDecision {
    /**
     * 已成功建立路徑。
     *
     * @property segments 依播放順序排列的非空路徑段。
     */
    data class Completed(val segments: List<TileWallMotionSegment>) : TileWallMotionPathDecision {
        init {
            require(segments.isNotEmpty()) { "Completed wall motion path must contain at least one segment" }
        }
    }

    /**
     * 輸入無法用安全的單面或相鄰牆角路徑表達。
     *
     * @property reason 可供平台診斷的穩定原因。
     */
    data class Rejected(val reason: TileWallMotionPathRejection) : TileWallMotionPathDecision
}

/** 路徑規劃拒絕原因。 */
enum class TileWallMotionPathRejection {
    /** 開門來源與最終布局的牌張集合不一致。 */
    INCONSISTENT_LAYOUT,

    /** 起訖位置同時改變水平位置與垂直層級，無法判斷動作順序。 */
    MIXED_HORIZONTAL_AND_VERTICAL,

    /** 起訖位置跨越超過一個相鄰牆角。 */
    NON_ADJACENT_SIDES,
}

/**
 * 把兩個規則 placement 之間的移動規劃為不穿越桌面的版本無關路徑。
 *
 * 此類別只處理幾何，不讀桌況、不安排絕對時間，也不建立 entity 動畫佇列。
 */
object TileWallMotionPathPlanner {
    /** 依 [context] 規劃由 [start] 移至 [end] 的路徑。 */
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
        val endCoordinate = unwrapNear(startCoordinate, circularCoordinate(end, context.stacksPerSide), context.stacksPerSide)
        val traversedCornerCells = traversedCornerCells(startCoordinate, endCoordinate, context.stacksPerSide)
        if (traversedCornerCells.isEmpty()) {
            return TileWallMotionPathDecision.Completed(listOf(segment(startWorld, endWorld)))
        }
        if (traversedCornerCells.size != 1) {
            return TileWallMotionPathDecision.Rejected(TileWallMotionPathRejection.NON_ADJACENT_SIDES)
        }
        return TileWallMotionPathDecision.Completed(roundedCornerSegments(context, startWorld, endWorld))
    }

    /** 以直線入口、四分之一圓近似及直線出口建立相鄰牆面的貼桌路徑。 */
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
        val entry = pointToward(corner, HorizontalPoint(start.x, start.z), CORNER_RADIUS)
        val exit = pointToward(corner, HorizontalPoint(end.x, end.z), CORNER_RADIUS)
        val points = buildList {
            add(start)
            if (hypot(entry.x - start.x, entry.z - start.z) > POSITION_EPSILON) {
                add(start.withHorizontal(entry, start.yaw))
            }
            for (index in 1..CORNER_SUBDIVISIONS) {
                val progress = index.toDouble() / CORNER_SUBDIVISIONS
                val point = quadraticBezier(entry, corner, exit, progress)
                val yaw = start.yaw + shortestYawDeltaDegrees(start.yaw, end.yaw) * progress.toFloat()
                add(start.withHorizontal(point, yaw))
            }
            if (horizontalDistance(last(), end) > POSITION_EPSILON || last().yaw != end.yaw) add(end)
        }
        return points.zipWithNext(::segment)
    }

    /** 建立一段按世界距離換算時長的路徑。 */
    private fun segment(start: MahjongTileWallPlacement, end: MahjongTileWallPlacement): TileWallMotionSegment {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        val duration = ceil(distance * TICKS_PER_BLOCK).toInt().coerceAtLeast(MIN_SEGMENT_TICKS)
        return TileWallMotionSegment(start, end, duration)
    }

    /** 回傳 [from] 朝 [toward] 前進最多 [distance] 的點。 */
    private fun pointToward(from: HorizontalPoint, toward: HorizontalPoint, distance: Double): HorizontalPoint {
        val dx = toward.x - from.x
        val dz = toward.z - from.z
        val length = hypot(dx, dz)
        if (length <= distance) return toward
        return HorizontalPoint(from.x + dx / length * distance, from.z + dz / length * distance)
    }

    /** 計算二次貝茲曲線上的一點。 */
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

    /** 將 placement 轉成包含小數沿牆位移的環狀墩座標。 */
    private fun circularCoordinate(placement: TileWallPlacement, stacksPerSide: Int): Double = placement.position.side *
        stacksPerSide + placement.position.stack + placement.offset.alongWallStacks

    /** 將 [coordinate] 展開到最接近 [reference] 的同一圈表示。 */
    private fun unwrapNear(reference: Double, coordinate: Double, stacksPerSide: Int): Double {
        val circumference = stacksPerSide * SIDE_COUNT.toDouble()
        var result = coordinate
        while (result - reference > circumference / 2.0) result -= circumference
        while (result - reference < -circumference / 2.0) result += circumference
        return result
    }

    /**
     * 列出起訖座標經過的牆角格；每一面最後一墩到下一面第一墩之間的完整單位區間視為牆角。
     */
    private fun traversedCornerCells(start: Double, end: Double, stacksPerSide: Int): Set<Int> {
        val lower = floor(minOf(start, end)).toInt()
        val upper = floor(maxOf(start, end) - POSITION_EPSILON).toInt()
        if (upper < lower) return emptySet()
        return (lower..upper).filterTo(mutableSetOf()) { coordinate ->
            coordinate.mod(stacksPerSide) == stacksPerSide - 1
        }
    }

    /** 只替換 placement 的水平位置與 yaw。 */
    private fun MahjongTileWallPlacement.withHorizontal(point: HorizontalPoint, targetYaw: Float): MahjongTileWallPlacement = copy(
        x = point.x,
        z = point.z,
        yaw = targetYaw.mod(FULL_TURN_DEGREES),
    )

    /** 兩個世界 placement 的水平距離。 */
    private fun horizontalDistance(first: MahjongTileWallPlacement, second: MahjongTileWallPlacement): Double = hypot(
        second.x - first.x,
        second.z - first.z,
    )

    /** 牌牆固定由四個面組成。 */
    private const val SIDE_COUNT = 4

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
}

/** 路徑規劃內部使用的水平座標。 */
private data class HorizontalPoint(val x: Double, val z: Double) {
    /** 與指定桌心的平方距離。 */
    fun distanceSquaredFrom(centerX: Double, centerZ: Double): Double {
        val dx = x - centerX
        val dz = z - centerZ
        return dx * dx + dz * dz
    }
}
