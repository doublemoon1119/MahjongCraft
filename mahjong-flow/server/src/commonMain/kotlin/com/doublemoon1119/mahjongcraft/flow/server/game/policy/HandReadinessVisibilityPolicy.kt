package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.HandReadinessSnapshot
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 只為對局參與者本人產生目前手牌分析，不向旁觀者公開。 */
@Single
class HandReadinessVisibilityPolicy(
    private val moduleRegistry: MahjongModuleRegistry,
) {
    /** 未參與對局、規則未提供 analyzer 或目前未聽牌時回傳 `null`。 */
    fun snapshotFor(game: Game, observerId: Uuid): HandReadinessSnapshot? {
        val player = game.tableState.players.firstOrNull { it.id == observerId } ?: return null
        val module = moduleRegistry.getModule(game.tableState.config)
        val analyzer = module.createDiscardReadinessAnalyzer() ?: return null
        val analysis = analyzer.analyzeCurrentHand(game.tableState, player) ?: return null
        return HandReadinessSnapshot(module.id, analysis)
    }
}
