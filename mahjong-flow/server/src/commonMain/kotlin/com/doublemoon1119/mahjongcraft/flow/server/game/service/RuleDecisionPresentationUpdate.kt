package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.PlayerAreaPresentationState
import com.doublemoon1119.mahjongcraft.flow.common.game.service.PlayerAreaSequenceStep
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionPlayerAreaPresentationStep
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
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
        if (playerAreaSteps.any { it is RuleDecisionPlayerAreaPresentationStep.Replacement }) {
            publishSequence(publisher)
        } else {
            publishPerPlayer(publisher)
        }
        publisher.publishRuleStateUpdated(current.id)
        publisher.publishRoundInfoUpdated(current.id, current)
    }

    /**
     * 依 [playerAreaSteps] 的順序逐步呈現所有玩家的牌區變化；沒有參與任何一步、但牌區有變化的玩家直接更新。
     *
     * @param publisher 平台呈現出口。
     */
    private fun publishSequence(publisher: GamePresentationPublisher) {
        val seatOf = current.players.withIndex().associate { (seatIndex, player) -> player.id to seatIndex }
        val steps = playerAreaSteps.map { step ->
            val seatIndex = seatOf.getValue(step.playerId)
            when (step) {
                is RuleDecisionPlayerAreaPresentationStep.Replacement -> PlayerAreaSequenceStep(seatIndex, step.setAsideTileIds, step.drawnTileIds)
                is RuleDecisionPlayerAreaPresentationStep.Draw -> PlayerAreaSequenceStep(seatIndex, emptyList(), listOf(step.tileId))
                is RuleDecisionPlayerAreaPresentationStep.FollowUp -> error("A rule decision cannot mix replacement and draw follow-up steps")
            }
        }
        val sequencedSeats = steps.mapTo(mutableSetOf()) { it.seatIndex }
        val areas = current.players.withIndex().filter { (seatIndex, _) -> seatIndex in sequencedSeats }.map { (seatIndex, player) ->
            PlayerAreaPresentationState(
                seatIndex = seatIndex,
                standingTileIds = player.hand.tiles.map { it.id },
                drawnTileId = player.hand.lastDrawn?.id,
                melds = player.hand.melds.map { it.toPresentation(current.config.revealsClosedKanTiles) },
                setAsideTileIds = player.setAsideTiles.map { it.id },
            )
        }
        publisher.publishPlayerAreaSequence(current.id, areas, steps)
        current.players.forEachIndexed { seatIndex, player ->
            if (seatIndex !in sequencedSeats) publishChangedArea(publisher, seatIndex, player)
        }
    }

    /**
     * 每位玩家各自呈現自己的牌區變化：先補入摸牌位，再依序移出並補牌。
     *
     * @param publisher 平台呈現出口。
     */
    private fun publishPerPlayer(publisher: GamePresentationPublisher) {
        val stepsByPlayerId = playerAreaSteps.groupBy(RuleDecisionPlayerAreaPresentationStep::playerId)
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
    }

    /**
     * 牌區與套用前不同時，直接更新到目前的牌區。
     *
     * @param publisher 平台呈現出口。
     * @param seatIndex 玩家的座位 index。
     * @param player 目前的玩家。
     */
    private fun publishChangedArea(publisher: GamePresentationPublisher, seatIndex: Int, player: MahjongPlayer) {
        val oldPlayer = previous.players.firstOrNull { it.id == player.id }
        if (oldPlayer?.hand == player.hand && oldPlayer.setAsideTiles == player.setAsideTiles) return
        publisher.publishPlayerTilesUpdated(
            gameId = current.id,
            seatIndex = seatIndex,
            standingTileIds = player.hand.tiles.map { it.id },
            drawnTileId = player.hand.lastDrawn?.id,
            melds = player.hand.melds.map { it.toPresentation(current.config.revealsClosedKanTiles) },
            setAsideTileIds = player.setAsideTiles.map { it.id },
        )
    }
}
