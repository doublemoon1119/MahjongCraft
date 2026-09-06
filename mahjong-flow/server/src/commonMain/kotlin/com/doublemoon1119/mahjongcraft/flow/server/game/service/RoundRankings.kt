package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 依規則的回合排名比較器建立從一開始的名次 map，供各結算 request factory 共用。 */
internal fun roundRanksByPlayer(state: TableState, module: MahjongRuleModule<*>): Map<Uuid, Int> = state.players
    .sortedWith(module.compareForRoundRanking())
    .withIndex()
    .associate { (index, player) -> player.id to index + 1 }
