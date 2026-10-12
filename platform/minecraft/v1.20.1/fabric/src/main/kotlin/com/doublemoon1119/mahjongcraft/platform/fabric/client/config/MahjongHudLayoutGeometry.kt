package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlin.math.roundToInt

/**
 * 將合法移動範圍的比例轉換為不超出畫面的左上角座標。
 *
 * @param ratio 水平或垂直位置比例，會限制在 0.0 到 1.0。
 * @param screenSize 畫面軸向尺寸。
 * @param elementSize 預覽元素軸向尺寸。
 * @return 元素左上角在該軸的像素座標。
 */
fun hudCoordinate(ratio: Double, screenSize: Int, elementSize: Int): Int {
    val travel = (screenSize - elementSize).coerceAtLeast(0)
    return (travel * ratio.coerceIn(0.0, 1.0)).roundToInt()
}

/**
 * 將左上角座標轉回合法移動範圍的比例。
 *
 * @param coordinate 元素左上角在該軸的像素座標。
 * @param screenSize 畫面軸向尺寸。
 * @param elementSize 預覽元素軸向尺寸。
 * @return 限制在 0.0 到 1.0 的位置比例；元素沒有移動範圍時為 0.0。
 */
fun hudRatio(coordinate: Int, screenSize: Int, elementSize: Int): Double {
    val travel = (screenSize - elementSize).coerceAtLeast(0)
    return if (travel == 0) 0.0 else coordinate.coerceIn(0, travel).toDouble() / travel
}
