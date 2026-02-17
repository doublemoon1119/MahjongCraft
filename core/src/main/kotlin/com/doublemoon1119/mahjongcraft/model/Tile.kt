package com.doublemoon1119.mahjongcraft.model

/**
 * 定義麻將牌的基本模型。
 *
 * 採用密封類別 (Sealed Class) 結構以確保型別安全，並支持編譯時的窮舉檢查。
 * 包含數牌 (Numeric) 與字牌 (Honor) 兩大類別。
 */
sealed class Tile {

    /**
     * 代表萬、筒、條等具有數值的有序牌。
     *
     * @property suit 數牌的花色類型。
     * @property value 牌面數值，限定於 1 至 9 之間。
     * @property isRed 是否為赤寶牌。應用於日麻規則。
     * @throws IllegalArgumentException 當數值不在 1..9 範圍內時拋出。
     */
    data class Numeric(
        val suit: Suit,
        val value: Int,
        val isRed: Boolean = false
    ) : Tile() {
        init {
            require(value in 1..9) { "Numeric tile value must be between 1 and 9, current: $value" }
        }
    }

    /**
     * 代表風牌與三元牌等不具連續數值的字牌。
     */
    sealed class Honor : Tile() {
        /** 東風 */
        data object East : Honor()

        /** 南風 */
        data object South : Honor()

        /** 西風 */
        data object West : Honor()

        /** 北風 */
        data object North : Honor()

        /** 中 (紅中) */
        data object Red : Honor()

        /** 發 (發財) */
        data object Green : Honor()

        /** 白 (白板) */
        data object White : Honor()
    }

    /**
     * 花牌 (Flower/Season)
     * 台灣牌、廣東牌等使用。日本麻將則不使用。
     */
    sealed class Flower : Tile() {
        /** 春 */
        data object Spring : Flower()

        /** 夏 */
        data object Summer : Flower()

        /** 秋 */
        data object Autumn : Flower()

        /** 冬 */
        data object Winter : Flower()

        /** 梅 */
        data object Plum : Flower()   // 梅

        /** 蘭 */
        data object Orchid : Flower() // 蘭

        /** 竹 */
        data object Bamboo : Flower() // 竹

        /** 菊 */
        data object Chrysanthemum : Flower() // 菊
    }

    /**
     * 定義數牌的花色。
     */
    enum class Suit {
        /** 萬子 */
        Characters,

        /** 筒子 */
        Dots,

        /** 條子 (索子) */
        Bamboos
    }
}