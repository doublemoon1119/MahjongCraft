package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationWinner

/** 展示使用的 ARGB 色盤。 */
data class ShowcasePalette(val primary: Int, val secondary: Int, val accent: Int)

/** 第三方可組合的受控展示視覺層。 */
enum class ShowcaseVisualLayer {
    /** 由上方照向展示牌組的聚光。 */
    Spotlight,

    /** 抵達時短暫出現的放射三角光束。 */
    RadialBurst,

    /** 包圍徽記與牌組的光環。 */
    Halo,

    /** 展示區內持續生成的細小火花。 */
    SparkField,
}

/** showcase 階段可額外播放的宣告式音效。 */
data class ShowcaseSound(val soundId: String, val tickOffset: Int, val volume: Float = 1.0f, val pitch: Float = 1.0f)

/**
 * 第三方可登記的胡牌展示定義；起飛與收尾不在可自定義範圍內。
 *
 * @property cueKey 對應的展示理由 ID（[WinCelebrationWinner.cueIds] 中的一項）。
 * @property titleTranslationKey 標題 translation key。
 * @property titleImageResourceId 標題圖片資源 ID。
 * @property palette 展示色盤。
 * @property showcaseDurationTicks 展示階段長度。
 * @property layers 啟用的視覺層。
 * @property extraSounds 展示階段額外播放的音效。
 * @property priority 一位贏家有多個展示理由時的挑選優先序；數值大者優先，相同時依規則給的順序。
 * @property pausesContinuingRound 本局在胡牌後繼續時，播放這段展示期間仍在本局中的玩家是否要等它播完；
 * 不論設定為何，下一局都會等整段胡牌呈現結束才開始。
 */
data class WinCelebrationShowcaseDefinition(
    val cueKey: String,
    val titleTranslationKey: String,
    val titleImageResourceId: String,
    val palette: ShowcasePalette,
    val showcaseDurationTicks: Int = 160,
    val layers: Set<ShowcaseVisualLayer> = ShowcaseVisualLayer.entries.toSet(),
    val extraSounds: List<ShowcaseSound> = emptyList(),
    val priority: Int = 0,
    val pausesContinuingRound: Boolean = true,
) {
    init {
        require(cueKey.isNotBlank()) { "Cue key must not be blank" }
        require(titleTranslationKey.isNotBlank()) { "Title translation key must not be blank" }
        require(titleImageResourceId.isNotBlank()) { "Title image resource id must not be blank" }
        require(showcaseDurationTicks in 80..240) { "Showcase duration must be between 80 and 240 ticks" }
        require(extraSounds.all { it.tickOffset in 0 until showcaseDurationTicks }) {
            "Showcase sound offsets must be inside the showcase duration"
        }
    }
}
