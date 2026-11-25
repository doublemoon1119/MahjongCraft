package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.dyeTextureRgb
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerColor
import net.minecraft.world.item.DyeColor

/** 牌尺物品與實體共用的顏色：染色牌尺使用原版染料的顏色，無色牌尺使用接近玻璃的淡藍白色。 */
internal object MahjongRulerTint {
    /** 無色牌尺的顏色。 */
    private const val CLEAR_RGB: Int = 0xE4F1FF

    /** 無色牌尺的不透明度，比照透明玻璃，比染色牌尺更透明。 */
    private const val CLEAR_ALPHA: Int = 110

    /** 染色牌尺的不透明度。 */
    private const val DYED_ALPHA: Int = 180

    /** 完全不透明的 `0xAARRGGBB` 透明度位元。 */
    private const val OPAQUE: Int = 0xFF shl 24

    /**
     * 牌尺的顏色。
     *
     * @param color 牌尺顏色。
     * @return 以 `0xRRGGBB` 打包的顏色。
     */
    fun rgb(color: MahjongRulerColor): Int {
        val dye = color.dyeName?.let { DyeColor.byName(it, null) } ?: return CLEAR_RGB
        return dyeTextureRgb(dye)
    }

    /**
     * 牌尺物品的染色。物品染色以 `0xAARRGGBB` 打包，有的 Minecraft 版本會套用其中的不透明度，所以固定為完全不透明；物品圖示
     * 不做半透明。
     *
     * @param color 牌尺顏色。
     * @return 以 `0xAARRGGBB` 打包、完全不透明的顏色。
     */
    fun itemTint(color: MahjongRulerColor): Int = OPAQUE or rgb(color)

    /**
     * 牌尺的不透明度。
     *
     * @param color 牌尺顏色。
     * @return 0 到 255 的不透明度。
     */
    fun alpha(color: MahjongRulerColor): Int = if (color == MahjongRulerColor.CLEAR) CLEAR_ALPHA else DYED_ALPHA
}
