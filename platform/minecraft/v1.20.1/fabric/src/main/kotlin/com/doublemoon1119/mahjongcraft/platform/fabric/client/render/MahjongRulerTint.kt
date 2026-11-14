package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerColor
import net.minecraft.util.DyeColor

/** 牌尺物品與實體共用的顏色：染色牌尺使用原版染料的顏色，無色牌尺使用接近玻璃的淡藍白色。 */
internal object MahjongRulerTint {
    /** 無色牌尺的顏色。 */
    private const val CLEAR_RGB: Int = 0xE4F1FF

    /** 無色牌尺的不透明度，比照透明玻璃，比染色牌尺更透明。 */
    private const val CLEAR_ALPHA: Int = 110

    /** 染色牌尺的不透明度。 */
    private const val DYED_ALPHA: Int = 180

    /** 一個顏色通道的最大值。 */
    private const val CHANNEL_MAX: Float = 255.0f

    /** 紅色通道在打包顏色中的位移。 */
    private const val RED_SHIFT: Int = 16

    /** 綠色通道在打包顏色中的位移。 */
    private const val GREEN_SHIFT: Int = 8

    /**
     * 牌尺的顏色。
     *
     * @param color 牌尺顏色。
     * @return 以 `0xRRGGBB` 打包的顏色。
     */
    fun rgb(color: MahjongRulerColor): Int {
        val components = color.dyeName?.let { DyeColor.byName(it, null) }?.colorComponents ?: return CLEAR_RGB
        val (red, green, blue) = components.map { (it * CHANNEL_MAX).toInt().coerceIn(0, CHANNEL_MAX.toInt()) }
        return (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }

    /**
     * 牌尺的不透明度。
     *
     * @param color 牌尺顏色。
     * @return 0 到 255 的不透明度。
     */
    fun alpha(color: MahjongRulerColor): Int = if (color == MahjongRulerColor.CLEAR) CLEAR_ALPHA else DYED_ALPHA
}
