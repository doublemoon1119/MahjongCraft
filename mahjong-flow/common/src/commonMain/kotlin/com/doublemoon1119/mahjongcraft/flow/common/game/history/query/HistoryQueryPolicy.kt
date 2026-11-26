package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/**
 * 伺服器目前允許的歷史查詢政策。
 *
 * @property queryEnabled 是否允許任何歷史查詢。
 * @property allowAdministratorQuery 是否允許管理員使用所有對局查詢範圍。
 */
data class HistoryQueryPolicy(
    val queryEnabled: Boolean = true,
    val allowAdministratorQuery: Boolean = true,
)
