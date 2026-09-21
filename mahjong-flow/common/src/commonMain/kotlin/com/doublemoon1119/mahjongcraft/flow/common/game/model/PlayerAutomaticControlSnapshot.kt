package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.module.requireValidAutomaticControlIds
import kotlin.uuid.Uuid

/**
 * 可公開給單一玩家的本局自動操作權威狀態。
 *
 * @property gameId 所屬對局識別碼。
 * @property revision 建立快照時的 [Game.automaticControlRevision]。
 * @property supportedControlIds 當前規則允許玩家啟用的完整控制 ID。
 * @property enabledControlIds 該玩家目前已啟用的控制 ID。
 */
data class PlayerAutomaticControlSnapshot(
    val gameId: Uuid,
    val revision: Long,
    val supportedControlIds: Set<String>,
    val enabledControlIds: Set<String>,
) {
    init {
        require(revision >= 0L) { "Automatic control revision must not be negative" }
        requireValidAutomaticControlIds(supportedControlIds)
        requireValidAutomaticControlIds(enabledControlIds)
        require(enabledControlIds.all { it in supportedControlIds }) {
            "Enabled automatic controls must be supported by the current rule"
        }
    }
}
