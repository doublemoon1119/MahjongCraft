package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import kotlin.uuid.Uuid

/** 根據權威遊戲狀態決定指定讀取者可見的手牌範圍。 */
interface GameVisibilityPolicy {
    /**
     * 計算指定讀取者可顯示完整手牌的玩家識別碼。
     *
     * @param game 包含完整桌況與流程設定的權威遊戲。
     * @param observerId 欲取得快照的讀取者識別碼。
     * @return 快照中可顯示完整手牌的玩家識別碼集合。
     */
    fun resolveVisibleHandPlayerIds(game: Game, observerId: Uuid): Set<Uuid>
}
