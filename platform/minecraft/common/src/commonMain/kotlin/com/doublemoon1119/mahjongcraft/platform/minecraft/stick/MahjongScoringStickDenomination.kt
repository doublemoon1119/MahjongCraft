package com.doublemoon1119.mahjongcraft.platform.minecraft.stick

/**
 * 麻將點棒面額；固定四種，對應日麻點棒的通用面額慣例，目前不開放第三方擴充。
 *
 * 面額集合由平台固定定義，不提供執行期間的擴充登記。
 *
 * @property points 點棒代表的分數面額。
 */
enum class MahjongScoringStickDenomination(
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

    /**
     * 依固定順序循環至下一面額。
     *
     * @return 循環中的下一種面額。
     */
    fun next(): MahjongScoringStickDenomination = entries[(ordinal + 1) % entries.size]

    companion object {
        /**
         * 由同步或持久化名稱取得面額；無效值使用百分棒。
         *
         * @param name 穩定面額名稱。
         * @return 對應面額；未知或缺失名稱回傳百分棒。
         */
        fun fromNameOrDefault(name: String): MahjongScoringStickDenomination = entries.firstOrNull { it.name == name } ?: P100
    }
}
