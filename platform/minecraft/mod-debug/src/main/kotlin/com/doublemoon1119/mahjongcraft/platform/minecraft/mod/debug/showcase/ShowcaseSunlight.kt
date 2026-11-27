package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 判斷牌桌的桌面照不照得到陽光。
 *
 * - 從桌面上平均分布的 [SAMPLES_PER_SIDE]×[SAMPLES_PER_SIDE] 個點往太陽的方向看出去，[RAY_LENGTH] 格內碰到方塊
 *   （例如樹葉、樹幹或山丘）就算在陰影裡。
 * - 太陽的方向與原版相同：從東邊升起、往西邊落下，正午在正上方。
 */
internal object ShowcaseSunlight {
    /** 每一邊取幾個點。 */
    const val SAMPLES_PER_SIDE = 4

    /** 往太陽的方向看出去多遠，單位為方塊。傍晚的太陽很低，遠處的樹也會把影子投到桌上。 */
    const val RAY_LENGTH = 48.0

    /** 往太陽的方向每次前進的距離，單位為方塊。 */
    const val RAY_STEP = 0.25

    /** 桌面中心到邊緣的水平距離。 */
    const val TABLE_HALF_WIDTH = 1.4

    /** 桌面比牌桌底部高多少。 */
    const val TABLE_TOP_HEIGHT = 1.0

    /**
     * 指向太陽的單位向量（x, y, z）。
     *
     * @param timeOfDay 原版一天的進度，0 是正午，0.25 是日落。
     * @return 指向太陽的方向。
     */
    fun sunDirection(timeOfDay: Float): Triple<Double, Double, Double> {
        val angle = timeOfDay * 2.0 * PI
        return Triple(-sin(angle), cos(angle), 0.0)
    }

    /**
     * 桌面照得到陽光的比例。
     *
     * @param x 牌桌中心方塊的 x 座標。
     * @param y 牌桌放置的高度。
     * @param z 牌桌中心方塊的 z 座標。
     * @param sun 指向太陽的單位向量，見 [sunDirection]。
     * @param blocked 指定方塊座標有沒有擋光的方塊。牌桌本身還沒有放下時不會擋到自己。
     * @return 照得到陽光的點所佔的比例，0 到 1。
     */
    fun litFraction(x: Int, y: Int, z: Int, sun: Triple<Double, Double, Double>, blocked: (x: Int, y: Int, z: Int) -> Boolean): Double {
        val (sunX, sunY, sunZ) = sun
        val offsets = (0 until SAMPLES_PER_SIDE).map { index -> -TABLE_HALF_WIDTH + 2 * TABLE_HALF_WIDTH * index / (SAMPLES_PER_SIDE - 1) }
        val points = offsets.flatMap { dx -> offsets.map { dz -> Triple(x + 0.5 + dx, y + TABLE_TOP_HEIGHT, z + 0.5 + dz) } }
        val steps = (RAY_LENGTH / RAY_STEP).toInt()
        val lit = points.count { (startX, startY, startZ) ->
            (1..steps).none { step ->
                val distance = step * RAY_STEP
                blocked(
                    floor(startX + sunX * distance).toInt(),
                    floor(startY + sunY * distance).toInt(),
                    floor(startZ + sunZ * distance).toInt(),
                )
            }
        }
        return lit.toDouble() / points.size
    }
}
