package com.doublemoon1119.mahjongcraft.logic.base

/**
 * 定義麻將牌的基本模型。
 *
 * 採用密封類別 (Sealed Class) 結構以確保型別安全，並支持編譯時的窮舉檢查。
 * 包含數牌 (Numeric)、字牌 (Honor) 以及花牌 (Flower) 三大類別。
 */
sealed class Tile {

    /**
     * 代表萬、筒、條等具有數值的有序牌。
     *
     * @property suit 數牌的花色類型，參考 [Suit]。
     * @property value 牌面數值，限定於 1 至 9 之間。
     * @property isRed 是否為赤寶牌 (Aka Dora)。主要應用於日本麻將規則。
     * @throws IllegalArgumentException 當數值不在 1..9 範圍內時拋出。
     */
    data class Numeric(
        val suit: Suit,
        val value: Int,
        val isRed: Boolean = false,
    ) : Tile() {
        init {
            require(value in 1..9) { "Numeric tile value must be between 1 and 9, current: $value" }
        }
    }

    /**
     * 代表風牌與三元牌等不具連續數值的字牌。
     * 日文稱之為「字牌 (Jiihai)」。
     */
    sealed class Honor : Tile() {
        /** 東風 (Ton) */
        data object East : Honor()

        /** 南風 (Nan) */
        data object South : Honor()

        /** 西風 (Shi) */
        data object West : Honor()

        /** 北風 (Pei) */
        data object North : Honor()

        /** 中 (Chun / 紅中) */
        data object Red : Honor()

        /** 發 (Hatsu / 發財) */
        data object Green : Honor()

        /** 白 (Haku / 白板) */
        data object White : Honor()
    }

    /**
     * 代表花牌與四季牌。
     * 主要應用於台灣麻將、廣東麻將等規則，日本麻將則不使用此類別。
     */
    sealed class Flower : Tile() {
        /** 春 (Spring) */
        data object Spring : Flower()

        /** 夏 (Summer) */
        data object Summer : Flower()

        /** 秋 (Autumn) */
        data object Autumn : Flower()

        /** 冬 (Winter) */
        data object Winter : Flower()

        /** 梅 (Plum) */
        data object Plum : Flower()

        /** 蘭 (Orchid) */
        data object Orchid : Flower()

        /** 竹 (Bamboo) */
        data object Bamboo : Flower()

        /** 菊 (Chrysanthemum) */
        data object Chrysanthemum : Flower()
    }

    /**
     * 定義數牌（序數牌）的花色。
     *
     * 麻將中的數牌由三種花色組成，每種花色包含從一到九的數字。
     */
    enum class Suit {
        /** * 萬子 (Character)。
         * 日文：萬子 (Manzu)。
         */
        Character,

        /** * 筒子 (Dot)。
         * 日文：筒子 (Pinzu)。
         */
        Dot,

        /** * 條子 (Bamboo)。
         * 日文：索子 (Sozu)。
         */
        Bamboo,
    }
}
