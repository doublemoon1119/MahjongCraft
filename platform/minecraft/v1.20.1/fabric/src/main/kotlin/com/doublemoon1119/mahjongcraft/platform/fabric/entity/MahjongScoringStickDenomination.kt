package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/** 麻將點棒面額；固定四種，對應現實麻將點棒的通用面額慣例，不開放第三方擴充。 */
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

    /**
     * 供 item model predicate 使用的正規化值，落在 `[0, 1]` 區間。
     *
     * `FabricModelPredicateProviderRegistry.register` 底層實際接收的是 `ClampedModelPredicateProvider`，
     * model override 比對讀的是夾在 `[0, 1]` 的 `call()`，不是 lambda 實作的 `unclampedCall()` 原始
     * 回傳值；直接回傳 `ordinal`（0..3）會讓面額 2、3 的值被夾成 1.0，跟面額 1 撞在一起、外觀顯示成
     * 千分棒——這裡改成除以最大 ordinal 正規化，確保四個面額的值都落在 `[0, 1]` 內、彼此不重疊。
     */
    val normalizedPredicateValue: Float
        get() = ordinal.toFloat() / (entries.size - 1)

    companion object {
        /** 由 ordinal 取得面額；無效值使用百分棒。 */
        fun fromOrdinalOrDefault(ordinal: Int): MahjongScoringStickDenomination = entries.getOrElse(ordinal) { P100 }
    }
}
