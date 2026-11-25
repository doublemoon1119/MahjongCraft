package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

/**
 * 測試牌譜腳本中的一個決定。
 *
 * 什麼時候執行：
 *
 * - 輪到自己時的決定：下一次輪到這家時。
 * - 回應捨牌的決定：下一次可以這樣回應時。其餘回應一律放過。
 * - 回答規則選擇的決定：規則下一次問這家時。
 */
sealed interface HistoryFixtureAction {
    /** 是否是輪到自己時的決定。 */
    val ownTurn: Boolean

    /**
     * 打出指定的牌。
     *
     * @property tile 要打出的牌。
     */
    data class Discard(val tile: HistoryFixtureTile) : HistoryFixtureAction {
        override val ownTurn: Boolean = true
    }

    /**
     * 立直並打出指定的牌。
     *
     * @property tile 立直宣言牌。
     */
    data class Riichi(val tile: HistoryFixtureTile) : HistoryFixtureAction {
        override val ownTurn: Boolean = true
    }

    /** 自摸和牌。 */
    data object Tsumo : HistoryFixtureAction {
        override val ownTurn: Boolean = true
    }

    /**
     * 以手中的牌暗槓。
     *
     * @property tile 槓的牌。
     */
    data class ClosedKan(val tile: HistoryFixtureTile) : HistoryFixtureAction {
        override val ownTurn: Boolean = true
    }

    /**
     * 在已碰的刻子上加槓。
     *
     * @property tile 加上去的牌。
     */
    data class AddedKan(val tile: HistoryFixtureTile) : HistoryFixtureAction {
        override val ownTurn: Boolean = true
    }

    /**
     * 碰別家打出的牌。
     *
     * @property tile 碰的牌。
     */
    data class Pon(val tile: HistoryFixtureTile) : HistoryFixtureAction {
        override val ownTurn: Boolean = false
    }

    /**
     * 吃上家打出的牌。
     *
     * @property handTiles 用來吃的兩張手牌。
     */
    data class Chi(val handTiles: List<HistoryFixtureTile>) : HistoryFixtureAction {
        override val ownTurn: Boolean = false

        init {
            require(handTiles.size == 2) { "A chi uses two tiles from the hand" }
        }
    }

    /**
     * 以手中三張牌明槓別家打出的牌。
     *
     * @property tile 槓的牌。
     */
    data class OpenKan(val tile: HistoryFixtureTile) : HistoryFixtureAction {
        override val ownTurn: Boolean = false
    }

    /** 榮和別家打出或加槓的牌。 */
    data object Ron : HistoryFixtureAction {
        override val ownTurn: Boolean = false
    }

    /** 拔北：把手中的北放到一旁並從嶺上補一張牌，例如日麻三人麻將的拔北。 */
    data object PullNorth : HistoryFixtureAction {
        override val ownTurn: Boolean = true
    }

    /** 宣告規則此時允許的途中流局，例如日麻的九種九牌。 */
    data object DeclareExhaustiveDraw : HistoryFixtureAction {
        override val ownTurn: Boolean = true
    }

    /** 規則問要不要以花牌和牌時選擇和牌，例如台灣麻將的八仙過海與七搶一。沒有這個決定時一律放棄。 */
    data object AcceptSpecialFlowerWin : HistoryFixtureAction {
        override val ownTurn: Boolean = false
    }
}
