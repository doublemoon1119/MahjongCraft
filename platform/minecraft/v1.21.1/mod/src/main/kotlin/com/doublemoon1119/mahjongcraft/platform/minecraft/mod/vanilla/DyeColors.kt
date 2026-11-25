package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.world.item.DyeColor

/**
 * 染料在材質著色時使用的顏色。
 *
 * @param dye 染料。
 * @return 以 `0xRRGGBB` 打包的顏色。
 */
fun dyeTextureRgb(dye: DyeColor): Int = dye.textureDiffuseColor and RGB_MASK

/** 去掉不透明度、只留紅綠藍的遮罩。 */
private const val RGB_MASK = 0xFFFFFF
