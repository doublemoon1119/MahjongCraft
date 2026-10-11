package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/** showcase 編隊中的一個牌位。 */
sealed interface ShowcaseCardSlot {
    /**
     * 某一翼的手牌卡片。
     *
     * @property wingIndex 所屬翼的索引。
     * @property order 卡片在翼中的順序；0 的 x 最大，依序往 x 較小的一側排。
     */
    data class Hand(
        val wingIndex: Int,
        val order: Int,
    ) : ShowcaseCardSlot

    /** 全部翼共用的一張和牌張。 */
    data object WinningTile : ShowcaseCardSlot
}
