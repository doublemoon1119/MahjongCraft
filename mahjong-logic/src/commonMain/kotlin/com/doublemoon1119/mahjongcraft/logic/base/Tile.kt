package com.doublemoon1119.mahjongcraft.logic.base

/**
 * 定義麻將牌的基本模型。
 *
 * 採用密封類別 (Sealed Class) 結構以確保型別安全，並支持編譯時的窮舉檢查。
 * 包含數牌 (Numeric)、字牌 (Honor) 以及由 registry 識別的擴充牌 (Extension)，花牌等地區限定牌種
 * 一律以 [Extension] 表示。
 */
sealed class Tile {

    /**
     * 代表由內建規則或第三方 extension 註冊的非共用牌種。
     *
     * 權威狀態只保存 [typeId]；實際張數、排序與特殊行為由目前規則解析，不由此值物件推論。
     *
     * @property typeId 指向 runtime [com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry] 的穩定 ID。
     */
    data class Extension(val typeId: TileTypeId) : Tile()

    /**
     * 代表萬、筒、條等具有數值的有序牌。
     *
     * @property suit 數牌的花色類型，參考 [Suit]。
     * @property value 牌面數值，限定於 1 至 9 之間。
     * @throws IllegalArgumentException 當數值不在 1..9 範圍內時拋出。
     */
    data class Numeric(
        val suit: Suit,
        val value: Int,
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
