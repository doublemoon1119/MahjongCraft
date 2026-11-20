package com.doublemoon1119.mahjongcraft.platform.minecraft.history

/** 牌譜回放中一家手牌的攤牌方式。 */
sealed interface HandReveal {
    /**
     * 胡牌倒牌。
     *
     * @property winningTile 自摸時單獨先倒下的那張牌的局內牌索引；不是自摸時為 null。
     */
    data class Win(val winningTile: Int?) : HandReveal

    /** 流局時公開倒牌。 */
    data object DrawRevealed : HandReveal

    /** 流局時不公開，手牌蓋起來。 */
    data object DrawConcealed : HandReveal
}
