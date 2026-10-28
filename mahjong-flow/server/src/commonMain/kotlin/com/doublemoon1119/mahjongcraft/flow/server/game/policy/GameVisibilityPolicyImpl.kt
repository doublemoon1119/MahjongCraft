package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.HandTilePose
import com.doublemoon1119.mahjongcraft.logic.module.HandTileState
import com.doublemoon1119.mahjongcraft.logic.module.HandTileVisibility
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.resolveHandTileDescriptions
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import kotlin.uuid.Uuid

/**
 * [GameVisibilityPolicy] 的預設實作。
 *
 * @property moduleRegistry 取得規則模組，用來裁切玩家狀態、額外可見牌面與逐張手牌姿態。
 */
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
        val snapshot = game.tableState.toSnapshot(visibleHandPlayerIds, module::setAsideTiles)
        val authoritativePlayers = game.tableState.players.associateBy { it.id }
        val canObserve = observerId in playerIds || game.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED
        val handTiles = game.tableState.players.flatMap { it.hand.standingTiles }.associateBy { it.id }
        val descriptions = if (canObserve) {
            try {
                module.resolveHandTileDescriptions(game.tableState)
            } catch (invalid: IllegalArgumentException) {
                violation(invalid.message ?: "Invalid hand tile description")
            }
        } else {
            emptyList()
        }
        val additionalIds = descriptions.filter { it.visibility == HandTileVisibility.Public }.mapTo(mutableSetOf()) { it.tileId }
        val revealedById = (snapshot.revealedHandTiles + additionalIds.map(handTiles::getValue)).associateBy { it.id }
        return snapshot.copy(
            handTileStates = descriptions.filter { it.pose != HandTilePose.UPRIGHT }.map { HandTileState(it.tileId, it.pose) },
            players = snapshot.players.map { playerSnapshot ->
                val player = checkNotNull(authoritativePlayers[playerSnapshot.id])
                playerSnapshot.copy(
                    playerRuleState = module.projectPlayerRuleState(game.tableState, player, observerId),
                    hand = playerSnapshot.hand.copy(
                        standingTiles = playerSnapshot.hand.standingTiles.map { tile ->
                            revealedById[tile.id]?.let { tile.copy(tile = it.tile) } ?: tile
                        },
                        lastDrawn = playerSnapshot.hand.lastDrawn?.let { tile ->
                            revealedById[tile.id]?.let { tile.copy(tile = it.tile) } ?: tile
                        },
                    ),
                )
            },
            dynamicRuleState = module.projectDynamicRuleState(game.tableState, observerId),
        )
    }

    /** 以規則契約錯誤中止投影，避免輸出錯誤的公開範圍。 */
    private fun violation(diagnostic: String): Nothing = throw RuleContractViolationException(
        GameAbortReasonIds.RULE_EXECUTION_FAILED,
        diagnostic,
    )
}
