package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * [GameVisibilityPolicy] 的預設實作。
 *
 * @property moduleRegistry 取得這一局規則模組，用來帶出各玩家移出手牌、公開擺在桌上的牌。
 */
@Single(binds = [GameVisibilityPolicy::class])
class GameVisibilityPolicyImpl(
    private val moduleRegistry: MahjongModuleRegistry,
) : GameVisibilityPolicy {
    override fun snapshotFor(game: Game, observerId: Uuid): TableStateSnapshot {
        val playerIds = game.tableState.players.mapTo(linkedSetOf()) { it.id }
        val winnerIds = game.tableState.players.filter { player ->
            player.actionHistory.any { it is GameAction.Tsumo || it is GameAction.Ron }
        }.mapTo(linkedSetOf()) { it.id }
        val visibleHandPlayerIds = if (observerId in playerIds) {
            winnerIds + observerId
        } else {
            val canRevealSpectatorHands =
                game.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED &&
                    game.flowConfig.spectatorHandVisibility == SpectatorHandVisibility.REVEALED
            when {
                canRevealSpectatorHands -> playerIds
                game.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED -> winnerIds
                else -> emptySet()
            }
        }
        val module = moduleRegistry.getModule(game.tableState.config)
        return game.tableState.toSnapshot(visibleHandPlayerIds, module::setAsideTiles)
    }
}
