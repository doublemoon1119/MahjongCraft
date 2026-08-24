package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

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
 */
data class WinCelebrationShowcaseDefinition(
    val cueKey: String,
    val titleTranslationKey: String,
    val titleImageResourceId: String,
    val palette: ShowcasePalette,
    val showcaseDurationTicks: Int = 160,
    val layers: Set<ShowcaseVisualLayer> = ShowcaseVisualLayer.entries.toSet(),
    val extraSounds: List<ShowcaseSound> = emptyList(),
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
