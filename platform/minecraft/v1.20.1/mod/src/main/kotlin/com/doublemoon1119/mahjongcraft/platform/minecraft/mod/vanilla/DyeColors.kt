package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.world.item.DyeColor

/**
 * 染料在材質著色時使用的顏色。
 *
 * @param dye 染料。
 * @return 以 `0xRRGGBB` 打包的顏色。
 */
fun dyeTextureRgb(dye: DyeColor): Int {
    val (red, green, blue) = dye.textureDiffuseColors.map { (it * CHANNEL_MAX).toInt().coerceIn(0, CHANNEL_MAX) }
    return (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}

/** 單一色版的最大值。 */
private const val CHANNEL_MAX = 255

/** 紅色在打包顏色中的位移。 */
private const val RED_SHIFT = 16

/** 綠色在打包顏色中的位移。 */
private const val GREEN_SHIFT = 8
