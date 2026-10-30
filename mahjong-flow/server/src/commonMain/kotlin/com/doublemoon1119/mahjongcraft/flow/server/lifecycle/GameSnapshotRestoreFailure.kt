package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import kotlin.uuid.Uuid

/**
 * 已解碼的對局無法產生觀察者快照，與存檔讀取失敗分開回報。
 *
 * @property gameId 無法完成投影的對局。
 * @property ruleModuleId 該對局使用的規則。
 * @property cause 規則投影失敗的原因，只提供給伺服器診斷。
 */
data class GameSnapshotRestoreFailure(
    val gameId: Uuid,
    val ruleModuleId: String,
    val cause: Throwable,
)
