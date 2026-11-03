package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import org.joml.Matrix4f
import org.joml.Quaternionf
import kotlin.math.atan

/**
 * 房間玩家預覽的固定基線、比例與游標偏移，其他模型沿用同一版面。
 *
 * @property centerX 模型水平中心。
 * @property feetY 模型腳底基線。
 * @property scale 原有玩家的每單位繪製比例。
 * @property lookX 游標與模型中心的水平距離。
 * @property lookY 游標與原有視線參考點的垂直距離。
 */
internal data class RoomModelPreviewLayout(
    val centerX: Int,
    val feetY: Int,
    val scale: Int,
    val lookX: Float,
    val lookY: Float,
) {
    /** 原版預覽使用的垂直游標旋轉。 */
    fun pitchRotation(): Quaternionf = Quaternionf().rotateX(atan(lookY / 40f) * 20f * (Math.PI.toFloat() / 180f))

    /** 原版預覽的座標翻轉與垂直旋轉。 */
    fun modelRotation(): Quaternionf = Quaternionf().rotateZ(Math.PI.toFloat()).mul(pitchRotation())

    /**
     * 原版預覽只翻轉位置矩陣的 Z 軸，不能改成翻轉 Y 軸。
     *
     * @param modelScale 此模型使用的比例。
     * @return 交由繪製矩陣套用的位置縮放。
     */
    fun scaleMatrix(modelScale: Int): Matrix4f = Matrix4f().scaling(modelScale.toFloat(), modelScale.toFloat(), -modelScale.toFloat())

    /** 原版預覽的整體水平朝向。 */
    fun bodyYaw(): Float = 180f + atan(lookX / 40f) * 20f

    /** 原有房間玩家預覽的版面計算。 */
    companion object {
        /**
         * 按房間卡片計算原有玩家預覽位置，不以固定正方形填滿可用空間。
         *
         * @param x 卡片左界。
         * @param y 卡片頂端。
         * @param cardWidth 卡片寬度。
         * @param mouseX 游標水平位置。
         * @param mouseY 游標垂直位置。
         * @return 與房間玩家原有呼叫一致的展示參數。
         */
        fun forCard(x: Int, y: Int, cardWidth: Int, mouseX: Int, mouseY: Int): RoomModelPreviewLayout = RoomModelPreviewLayout(
            centerX = x + cardWidth / 2,
            feetY = y + 84,
            scale = (cardWidth / 3).coerceIn(24, 36),
            lookX = x + cardWidth / 2 - mouseX.toFloat(),
            lookY = y + 42 - mouseY.toFloat(),
        )
    }
}
