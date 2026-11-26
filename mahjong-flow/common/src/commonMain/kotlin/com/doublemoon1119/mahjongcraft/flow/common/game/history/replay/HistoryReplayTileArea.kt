package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/**
 * 玩家一個移出手牌、公開擺在桌上的牌區。
 *
 * @property areaId 牌區的完整識別碼，由規則定義。
 * @property tiles 牌區內的牌，依規則保存的順序排列。
 */
data class HistoryReplayTileArea(
    val areaId: String,
    val tiles: List<HistoryTileReference>,
)
