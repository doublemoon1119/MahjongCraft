package com.doublemoon1119.mahjongcraft.platform.minecraft.stick

/**
 * 麻將點棒面額；固定四種，對應日麻點棒的通用面額慣例，目前不開放第三方擴充。
 *
 * 若日後有規則模組使用不同面額或不使用點棒計分，這裡需要重新評估是否要開放成 registry。
 */
// TODO: 新增其他地區規則模組時重新評估此 enum 是否需要開放成 registry。
enum class MahjongScoringStickDenomination(
    /** 點棒代表的分數面額。 */
    val points: Int,
) {
    /** 百分棒。 */
    P100(100),

    /** 千分棒。 */
    P1000(1000),

    /** 五千分棒。 */
    P5000(5000),

    /** 萬分棒。 */
    P10000(10000),
    ;

    /** 依固定順序循環至下一面額。 */
    fun next(): MahjongScoringStickDenomination = entries[(ordinal + 1) % entries.size]

    companion object {
        /** 由同步或持久化名稱取得面額；無效值使用百分棒。 */
        fun fromNameOrDefault(name: String): MahjongScoringStickDenomination = entries.firstOrNull { it.name == name } ?: P100
    }
}
