package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.logic.config.dealBatchSizes
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 將 debug 情境轉交給正式 [GamePresentationPublisher] 的可測試發布器。 */
@Single
class DebugGameScenarioPresentationPublisher(
    private val publisher: GamePresentationPublisher,
    private val moduleRegistry: MahjongModuleRegistry,
) {
    /** 依情境指定的呈現方式發布完整桌況。 */
    fun publish(result: DebugGameScenarioResult) {
        val game = result.game
        val state = game.tableState
        val revealedTileIds = (state.dynamicRuleState as? TileWallRevealable)?.getVisibleTileIds(state).orEmpty()
        publisher.clearPlayerAreas(game.id)
        when (val presentation = result.presentation) {
            DebugGameScenarioPresentation.StaticWall -> publishStaticTable(result, revealedTileIds)
            is DebugGameScenarioPresentation.InitialRound -> {
                publisher.publishWallStructure(
                    gameId = game.id,
                    assemblyStructure = result.wallStructure,
                    layout = result.wallLayout,
                    dealerSeatIndex = state.dealerIndex,
                    deadWallTileIds = state.reservedWallTiles.mapTo(mutableSetOf()) { tile -> tile.id },
                    diceCount = presentation.diceRoll.values.size,
                    animateOpening = true,
                    revealedTileIds = revealedTileIds,
                )
                publisher.publishDiceRoll(
                    gameId = game.id,
                    dice = presentation.diceRoll,
                    dealerSeatIndex = state.dealerIndex,
                    roundNumber = state.roundNumber,
                    comboCount = state.comboCount,
                )
            }
        }
        publishTableInformation(result)
        (result.presentation as? DebugGameScenarioPresentation.InitialRound)?.let { presentation ->
            publisher.publishInitialDealAnimation(
                gameId = game.id,
                handTileIdsBySeatIndex = presentation.dealOrderHandTileIdsBySeatIndex,
                postFlipHandTileIdsBySeatIndex = presentation.postFlipHandTileIdsBySeatIndex,
                dealerSeatIndex = state.dealerIndex,
                comboStickCount = state.comboCount,
                dealBatchSizes = state.config.dealBatchSizes(),
                diceCount = presentation.diceRoll.values.size,
            )
        }
    }

    /** 直接重建可操作情境的目前牌牆、手牌、副露與牌河。 */
    private fun publishStaticTable(result: DebugGameScenarioResult, revealedTileIds: Set<Uuid>) {
        val game = result.game
        val state = game.tableState
        publisher.publishWallStructure(
            gameId = game.id,
            assemblyStructure = result.wallStructure,
            layout = result.wallLayout,
            dealerSeatIndex = state.dealerIndex,
            deadWallTileIds = state.reservedWallTiles.mapTo(mutableSetOf()) { tile -> tile.id },
            diceCount = 0,
            animateOpening = false,
            revealedTileIds = revealedTileIds,
        )
        state.players.forEachIndexed { seatIndex, player ->
            publisher.publishPlayerAreaUpdated(
                gameId = game.id,
                seatIndex = seatIndex,
                standingTileIds = player.hand.tiles.map { tile -> tile.id },
                drawnTileId = player.hand.lastDrawn?.id,
                melds = player.hand.melds.map { meld -> meld.toPresentation(state.config.revealsClosedKanTiles) },
                comboStickCount = state.comboCount.takeIf { state.isDealer(player.id) } ?: 0,
            )
            publisher.publishDiscardPileUpdated(
                gameId = game.id,
                seatIndex = seatIndex,
                discardTileIds = player.discardPile.entries.map { entry -> entry.tile.id },
                sidewaysMarkedTileId = (player.discardPile as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId(),
            )
        }
    }

    /** 發布靜態情境與完整開局共用的點棒、供託及局況資訊。 */
    private fun publishTableInformation(result: DebugGameScenarioResult) {
        val game = result.game
        val state = game.tableState
        val module = moduleRegistry.getModule(state.config)
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
