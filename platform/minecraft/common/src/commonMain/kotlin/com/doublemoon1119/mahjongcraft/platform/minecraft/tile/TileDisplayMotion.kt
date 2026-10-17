package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector

/**
 * 已同步的單段牌張動畫資料；只描述呈現，不代表任何遊戲操作權。
 *
 * @property startedAt 動畫開始的世界時間。
 * @property spec 動畫時長、弧線與翻轉參數。
 * @property startOffset 起點相對於共享終點的位置差。
 * @property startYawOffset 起點相對於共享終點的朝向差。
 */
data class TileDisplayMotion(
    val startedAt: Long,
    val spec: TileMotionAnimationSpec,
    val startOffset: DiceAnimationVector,
    val startYawOffset: Float,
) {
    /**
     * 依共享終點重建指定時間的實際顯示位置。
     *
     * @param target 共享動畫的終點。
     * @param time 本幀世界時間，包含部分 tick。
     * @return 已套用一次動畫的位置與姿態。
     */
    fun sample(target: TileDisplayTransform, time: Double): TileDisplayTransform {
        val frame = TileMotionAnimation(spec).frame(time - startedAt, startOffset, startYawOffset)
        return TileDisplayTransform(
            position = DiceAnimationVector(
                target.position.x + frame.offset.x,
                target.position.y + frame.offset.y,
                target.position.z + frame.offset.z,
            ),
            yawDegrees = target.yawDegrees + frame.yawOffsetDegrees,
            poseDegrees = frame.poseRotationDegrees,
        )
    }
}
