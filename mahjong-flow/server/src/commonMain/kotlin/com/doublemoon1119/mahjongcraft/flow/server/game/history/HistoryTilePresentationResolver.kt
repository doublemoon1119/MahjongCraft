package com.doublemoon1119.mahjongcraft.flow.server.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTilePresentation
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 在交易提交時解算每張牌的呈現結果，供牌譜保存。 */
fun interface HistoryTilePresentationResolver {
    /**
     * 解算目前所有牌區每張牌的呈現結果。
     *
     * @param tableState 交易提交後的權威桌況。
     * @return 依牌張識別碼索引的呈現結果。
     */
    fun resolve(tableState: TableState): Map<Uuid, HistoryTilePresentation>
}
