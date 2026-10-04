package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import kotlin.uuid.Uuid

/**
 * 一次權威交易對單一桌子提交的事實；不論這場是否記錄歷史都會送出。
 *
 * @property tableId 桌子 UUID。
 * @property previousGame 交易前的對局；交易建立新對局時為 null。
 * @property game 交易後的對局；交易移除對局時為 null。
 * @property facts 本次交易依序提交的事實，至少一筆。
 */
data class CommittedGameFacts(
    val tableId: Uuid,
    val previousGame: Game?,
    val game: Game?,
    val facts: List<HistoryEventDraft>,
) {
    init {
        require(previousGame != null || game != null) { "Committed facts must belong to a game" }
        require(facts.isNotEmpty()) { "Committed facts must not be empty" }
    }

    /** 事實所屬的場次。 */
    val matchId: Uuid get() = (game ?: checkNotNull(previousGame)).matchId
}
