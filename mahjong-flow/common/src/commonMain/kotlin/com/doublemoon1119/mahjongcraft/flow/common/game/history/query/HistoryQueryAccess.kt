package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import kotlin.uuid.Uuid

/**
 * 可信任的歷史查詢發起者資訊。
 *
 * @property principalId 發起查詢的玩家 UUID。
 * @property isAdministrator 發起者目前是否擁有管理員權限。
 */
data class HistoryQueryAccess(
    val principalId: Uuid,
    val isAdministrator: Boolean,
)
