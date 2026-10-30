package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 本局已和牌的玩家；他們的手牌對所有讀取者公開。
 *
 * @return 已和牌玩家的識別碼。
 */
internal fun TableState.winnerPlayerIds(): Set<Uuid> = players.filter { player ->
    player.actionHistory.any { it is GameAction.Tsumo || it is GameAction.Ron }
}.mapTo(linkedSetOf()) { it.id }
