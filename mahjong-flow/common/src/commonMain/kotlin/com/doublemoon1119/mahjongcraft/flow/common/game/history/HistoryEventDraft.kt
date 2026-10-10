package com.doublemoon1119.mahjongcraft.flow.common.game.history

import kotlin.uuid.Uuid

/**
 * 在權威交易內產生、尚未指派序號與時間戳的歷史事件。
 *
 * @property actorPlayerId 發起動作的玩家；系統或規則事件為 null。
 * @property fact 已提交的權威事實。
 */
data class HistoryEventDraft(
    val actorPlayerId: Uuid?,
    val fact: HistoryFact,
)
