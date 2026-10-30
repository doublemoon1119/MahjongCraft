package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch

/**
 * 同一次事件投影的批次與標記解析狀態，不保存原始事實或失敗物件。
 *
 * @property batch 公開活動批次；沒有公開活動時為 null。
 * @property markersFailed 此次投影是否發生可隔離的標記或局況解析失敗。
 */
class GameEventProjectionResult(
    val batch: CommittedMatchEventBatch?,
    val markersFailed: Boolean,
)
