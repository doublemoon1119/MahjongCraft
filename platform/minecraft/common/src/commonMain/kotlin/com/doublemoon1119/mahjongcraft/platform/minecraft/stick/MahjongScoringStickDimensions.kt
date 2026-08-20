package com.doublemoon1119.mahjongcraft.platform.minecraft.stick

/**
 * 麻將點棒世界呈現共用尺寸常數。
 *
 * 沿用重構前 main 分支 `mahjong_scoring_stick_base.json`／`MahjongScoringStickEntity` 已經過實際遊戲
 * 呈現驗證的初始比例（16×0.5×2.5 模型單位、`0.4` runtime 縮放），供 Fabric-only 的
 * `com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity` 與純 Kotlin
 * common 座標計算共用同一組數值，避免兩處各自定義後跑掉。
 */
object MahjongScoringStickDimensions {
    /** 舊版驗證過的點棒世界縮放。 */
    private const val STICK_SCALE: Double = 0.4

    /** 點棒世界寬度（沿桌面長邊）。 */
    const val STICK_WIDTH: Double = 16.0 / 16.0 * STICK_SCALE

    /** 點棒世界高度（厚度）。 */
    const val STICK_HEIGHT: Double = 0.5 / 16.0 * STICK_SCALE

    /** 點棒世界深度（沿桌面短邊）。 */
    const val STICK_DEPTH: Double = 2.5 / 16.0 * STICK_SCALE
}
