package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionPlayerAreaPresentationStep
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.tile.component.publicFaceUpWallTileIds

/**
 * 一次選擇步驟提交前後的呈現差異；只在權威交易成功後交付。
 *
 * @property previous 規則選擇結果套用前的桌況。
 * @property current 此次交易提交的最終桌況。
 * @property playerAreaSteps 此次交易中依序成立的玩家牌區變化。
 */
internal data class RuleDecisionPresentationUpdate(
    val previous: TableState,
    val current: TableState,
    val playerAreaSteps: List<RuleDecisionPlayerAreaPresentationStep> = emptyList(),
) {
    /**
     * 更新變動的牌區及局況，不重播初次發牌。
     *
     * @param publisher 平台呈現出口。
     * @param module 此桌況所屬的規則模組。
     * @param componentRegistry 正式組件與純效果來源。
     */
    fun publish(publisher: GamePresentationPublisher, module: MahjongRuleModule<*>, componentRegistry: ComponentRegistry) {
        if (previous == current) return
        val visibleWallTiles = module.publicFaceUpWallTileIds(current, componentRegistry)
        val previousVisibleWallTiles = module.publicFaceUpWallTileIds(previous, componentRegistry)
        val stepsByPlayerId = playerAreaSteps.groupBy(RuleDecisionPlayerAreaPresentationStep::playerId)
        current.physicalWallLayout?.let { layout ->
            if (previous.physicalWallLayout != layout || previousVisibleWallTiles != visibleWallTiles) {
                publisher.publishWallLayoutUpdated(
                    gameId = current.id,
                    layout = layout,
                    dealerSeatIndex = current.dealerIndex,
                    revealedTileIds = visibleWallTiles.intersect(layout.placements.keys),
                )
            }
        }
        current.players.forEachIndexed { seatIndex, player ->
            val oldPlayer = previous.players.firstOrNull { it.id == player.id }
            val setAsideTiles = player.setAsideTiles
            val oldSetAsideTiles = oldPlayer?.setAsideTiles.orEmpty()
            val steps = stepsByPlayerId[player.id].orEmpty()
            val draw = steps.firstOrNull() as? RuleDecisionPlayerAreaPresentationStep.Draw
            val followUpSteps = steps.drop(if (draw == null) 0 else 1).map { step ->
                check(step is RuleDecisionPlayerAreaPresentationStep.FollowUp) { "A rule decision draw must precede the player's other steps" }
                step.followUpStep
            }
            if (oldPlayer?.hand != player.hand || oldSetAsideTiles != setAsideTiles || steps.isNotEmpty()) {
                publisher.publishPlayerTilesUpdated(
                    gameId = current.id,
                    seatIndex = seatIndex,
                    standingTileIds = player.hand.tiles.map { it.id },
                    drawnTileId = player.hand.lastDrawn?.id,
                    melds = player.hand.melds.map { it.toPresentation(current.config.revealsClosedKanTiles) },
                    setAsideTileIds = setAsideTiles.map { it.id },
                    isNewlyDrawn = draw != null,
                    followUpSteps = followUpSteps,
                )
            }
        }
        publisher.publishRuleStateUpdated(current.id)
        publisher.publishRoundInfoUpdated(current.id, current)
    }
}
