package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 原生平台讀取後交給私人布局計算器的牌張資料，不持有世界或實體。
 *
 * @property target 共享位置的終點，不是動畫中的中間位置。
 * @property motion 目前的共享動畫；沒有時為 null。
 * @property visible 原生呈現目前是否已允許繪製這張牌。
 */
data class TileDisplayInput(
    val target: TileDisplayTransform,
    val motion: TileDisplayMotion?,
    val visible: Boolean,
) {
    /**
     * 取得尚未套用私人布局時的世界呈現。
     *
     * @param time 本幀世界時間。
     * @return 共享動畫當下的位置與姿態。
     */
    fun sample(time: Double): TileDisplayTransform = motion?.sample(target, time) ?: target
}
