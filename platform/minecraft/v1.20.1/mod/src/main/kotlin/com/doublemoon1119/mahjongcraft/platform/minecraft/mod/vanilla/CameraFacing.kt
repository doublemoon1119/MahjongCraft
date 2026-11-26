package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import org.joml.Quaternionf

/**
 * 面向鏡頭的轉向：套用後以 `(-s, -s, s)` 縮放的平面，正面朝向鏡頭、文字由左至右。
 *
 * @return 新的轉向。之後改動它不影響繪製器。
 */
fun EntityRenderDispatcher.cameraFacingRotation(): Quaternionf = cameraFacingRotation(cameraOrientation())

/**
 * 暫時指定面向鏡頭的轉向，之後 [cameraFacingRotation] 回傳它。用於畫面中的場景。
 *
 * @param rotation 面向鏡頭的轉向。
 */
fun EntityRenderDispatcher.overrideCameraFacingRotation(rotation: Quaternionf) {
    overrideCameraOrientation(cameraOrientationFacing(rotation))
}

/**
 * 原版鏡頭轉向對應的面向鏡頭轉向。
 *
 * @param orientation 原版的鏡頭轉向。
 * @return 新的面向鏡頭轉向。不改動傳入的轉向。
 */
internal fun cameraFacingRotation(orientation: Quaternionf): Quaternionf = Quaternionf(orientation)

/**
 * 面向鏡頭轉向對應的原版鏡頭轉向。
 *
 * @param facing 面向鏡頭的轉向。
 * @return 新的原版鏡頭轉向。不改動傳入的轉向。
 */
internal fun cameraOrientationFacing(facing: Quaternionf): Quaternionf = Quaternionf(facing)
