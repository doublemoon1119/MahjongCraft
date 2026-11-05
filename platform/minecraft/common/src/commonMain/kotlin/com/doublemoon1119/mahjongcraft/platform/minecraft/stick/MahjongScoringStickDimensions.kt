package com.doublemoon1119.mahjongcraft.platform.minecraft.stick

/**
 * 麻將點棒世界呈現共用尺寸常數。
 *
 * 點棒的模型是 16×0.5×2.5 模型單位、runtime 縮放 `0.4`。Fabric 的 `MahjongScoringStickEntity` 與 common 的
 * 座標計算共用同一組數值，避免兩處各自定義而不一致。
 */
object MahjongScoringStickDimensions {
    /** 點棒世界縮放係數。 */
    private const val STICK_SCALE: Double = 0.4

    /** 點棒世界寬度（沿桌面長邊）。 */
    const val STICK_WIDTH: Double = 16.0 / 16.0 * STICK_SCALE

    /** 點棒世界高度（厚度）。 */
    const val STICK_HEIGHT: Double = 0.5 / 16.0 * STICK_SCALE

    /** 點棒世界深度（沿桌面短邊）。 */
    const val STICK_DEPTH: Double = 2.5 / 16.0 * STICK_SCALE
}
