package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/** 可查詢歷史的權限範圍。 */
enum class HistoryQueryScope {
    /** 只查詢目前使用者參與的對局。 */
    OWN,

    /** 查詢伺服器上所有已公開的對局。 */
    ALL,
}
