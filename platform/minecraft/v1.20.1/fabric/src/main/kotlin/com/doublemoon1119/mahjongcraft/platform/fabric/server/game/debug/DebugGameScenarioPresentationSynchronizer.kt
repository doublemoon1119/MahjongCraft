package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTablePresentationCleaner
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single

/** 使用正式 presentation contract 靜態重建 debug scenario 的完整桌面呈現。 */
@Single
class DebugGameScenarioPresentationSynchronizer(
    private val publisher: GamePresentationPublisher,
    private val moduleRegistry: MahjongModuleRegistry,
    private val serverHolder: FabricServerHolder,
    private val tableLocationRegistry: TableLocationRegistry,
    private val presentationCleaner: FabricTablePresentationCleaner,
) {
    /** 清除上一個玩家區並從權威 scenario 結果完整發布新桌況。 */
    fun synchronize(result: DebugGameScenarioResult) {
        val game = result.game
        val state = game.tableState
        val module = moduleRegistry.getModule(state.config)
        val revealedTileIds = (state.dynamicRuleState as? TileWallRevealable)?.getVisibleTileIds(state).orEmpty()
        val location = requireNotNull(tableLocationRegistry.get(game.id)?.location) { "No table location for debug scenario" }
        val dimensionId = requireNotNull(Identifier.tryParse(location.dimensionId)) { "Invalid table dimension ID" }
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, dimensionId)
        val world = requireNotNull(serverHolder.current()?.getWorld(worldKey)) { "Table world is not loaded" }
        presentationCleaner.clear(world, game.id, BlockPos(location.x, location.y, location.z))
        publisher.clearPlayerAreas(game.id)
        publisher.publishWallStructure(
            gameId = game.id,
            structure = result.wallStructure,
            dealerSeatIndex = state.dealerIndex,
            deadWallTileIds = state.reservedWallTiles.mapTo(mutableSetOf()) { it.id },
            diceCount = 0,
            revealedTileIds = revealedTileIds,
        )
        state.players.forEachIndexed { seatIndex, player ->
            publisher.publishPlayerAreaUpdated(
                gameId = game.id,
                seatIndex = seatIndex,
                standingTileIds = player.hand.tiles.map { it.id },
                drawnTileId = player.hand.lastDrawn?.id,
                melds = player.hand.melds.map { it.toPresentation(state.config.revealsClosedKanTiles) },
                comboStickCount = state.comboCount.takeIf { state.isDealer(player.id) } ?: 0,
            )
            publisher.publishDiscardPileUpdated(
                gameId = game.id,
                seatIndex = seatIndex,
                discardTileIds = player.discardPile.entries.map { it.tile.id },
                sidewaysMarkedTileId = (player.discardPile as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId(),
            )
        }
        publisher.publishScoringSticksUpdated(game.id, state.dealerIndex, state.comboCount)
        publisher.publishStickPotUpdated(
            gameId = game.id,
            declaredSeatIndices = state.players.mapIndexedNotNull { index, player ->
                index.takeIf { module.isPlayerInRiichi(player) }
            }.toSet(),
            dealerSeatIndex = state.dealerIndex,
            comboStickCount = state.comboCount,
            pooledStickCount = module.getStickPotCount(state),
        )
        publisher.publishRoundInfoUpdated(game.id, module.getRoundInfoLines(state))
    }
}
