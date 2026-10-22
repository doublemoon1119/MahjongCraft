package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable

/**
 * 一次準備步驟提交前後的呈現差異；只在權威交易成功後交付。
 *
 * @property previous 準備結果套用前的桌況。
 * @property current 此次交易提交的最終桌況。
 */
internal data class RoundPreparationPresentationUpdate(
    val previous: TableState,
    val current: TableState,
) {
    /**
     * 更新變動的牌區及局況，不重播初次發牌。
     *
     * @param publisher 平台呈現出口。
     * @param module 此桌況所屬的規則模組。
     */
    fun publish(publisher: GamePresentationPublisher, module: MahjongRuleModule<*>) {
        if (previous == current) return
        val visibleWallTiles = (current.dynamicRuleState as? TileWallRevealable)?.getVisibleTileIds(current).orEmpty()
        val previousVisibleWallTiles = (previous.dynamicRuleState as? TileWallRevealable)?.getVisibleTileIds(previous).orEmpty()
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
            val setAsideTiles = module.setAsideTiles(player)
            val oldSetAsideTiles = oldPlayer?.let(module::setAsideTiles).orEmpty()
            if (oldPlayer?.hand != player.hand || oldSetAsideTiles != setAsideTiles) {
                publisher.publishPlayerTilesUpdated(
                    gameId = current.id,
                    seatIndex = seatIndex,
                    standingTileIds = player.hand.tiles.map { it.id },
                    drawnTileId = player.hand.lastDrawn?.id,
                    melds = player.hand.melds.map { it.toPresentation(current.config.revealsClosedKanTiles) },
                    setAsideTileIds = setAsideTiles.map { it.id },
                )
            }
        }
        publisher.publishRuleStateUpdated(current.id)
        publisher.publishRoundInfoUpdated(current.id, current)
    }
}
