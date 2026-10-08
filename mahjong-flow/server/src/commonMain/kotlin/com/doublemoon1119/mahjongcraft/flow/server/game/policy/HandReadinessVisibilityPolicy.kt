package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.HandReadinessSnapshot
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.visibleTiles
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 只為對局參與者本人產生目前手牌分析，不向旁觀者公開。
 *
 * @property moduleRegistry 取得這一局規則模組的手牌分析器。
 * @property visibilityPolicy 產生參與者本人的桌況快照，剩餘張數只扣除其中看得到的牌。
 */
@Single
class HandReadinessVisibilityPolicy(
    private val moduleRegistry: MahjongModuleRegistry,
    private val visibilityPolicy: GameVisibilityPolicy,
) {
    /** 未參與對局、規則未提供 analyzer 或目前未聽牌時回傳 `null`。 */
    fun snapshotFor(game: Game, observerId: Uuid): HandReadinessSnapshot? {
        val player = game.tableState.players.firstOrNull { it.id == observerId } ?: return null
        val module = moduleRegistry.getModule(game.tableState.config)
        val analyzer = module.createDiscardReadinessAnalyzer() ?: return null
        val analysis = analyzer.analyzeCurrentHand(
            tableState = game.tableState,
            player = player,
            visibleTiles = visibilityPolicy.snapshotFor(game, observerId).visibleTiles(),
        ) ?: return null
        return HandReadinessSnapshot(module.id, analysis)
    }
}
