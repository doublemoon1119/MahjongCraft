package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.logic.config.dealBatchSizes
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import kotlin.uuid.Uuid

/**
 * 將開發情境轉交給正式 [GamePresentationPublisher] 的發布器。
 *
 * @property publisher 發布桌面物件與局況的正式呈現介面。
 * @property moduleRegistry 查詢牌張排序與移出手牌區等規則能力。
 */
class DebugGameScenarioPresentationPublisher(
    private val publisher: GamePresentationPublisher,
    private val moduleRegistry: MahjongModuleRegistry,
) {
    /**
     * 依情境指定的呈現方式發布完整桌況。
     *
     * @param result 已通過情境驗證的對局與呈現資料。
     */
    fun publish(result: DebugGameScenarioResult) {
        val game = result.game
        val state = game.tableState
        val revealedTileIds = (state.dynamicRuleState as? TileWallRevealable)?.getVisibleTileIds(state).orEmpty()
        publisher.clearPlayerTiles(game.id)
        when (val presentation = result.presentation) {
            DebugGameScenarioPresentation.StaticWall -> publishStaticTable(result, revealedTileIds)
            is DebugGameScenarioPresentation.InitialRound -> {
                publisher.publishWallStructure(
                    gameId = game.id,
                    assemblyStructure = result.wallStructure,
                    layout = result.wallLayout,
                    dealerSeatIndex = state.dealerIndex,
                    deadWallTileIds = state.reservedWallTiles.mapTo(mutableSetOf()) { tile -> tile.id },
                    openingDiceCounts = listOf(presentation.diceRoll.values.size),
                    isNewOpening = true,
                    revealedTileIds = revealedTileIds,
                )
                publisher.publishOpeningRolls(
                    gameId = game.id,
                    rolls = listOf(OpeningDiceRoll(0, state.players[state.dealerIndex].id, presentation.diceRoll)),
                )
            }
        }
        publishTableInformation(result)
        (result.presentation as? DebugGameScenarioPresentation.InitialRound)?.let { presentation ->
            publisher.publishInitialDeal(
                gameId = game.id,
                handTileIdsBySeatIndex = presentation.dealOrderHandTileIdsBySeatIndex,
                postFlipHandTileIdsBySeatIndex = presentation.postFlipHandTileIdsBySeatIndex,
                dealerSeatIndex = state.dealerIndex,
                dealBatchSizes = state.config.dealBatchSizes(),
                openingDiceCounts = listOf(presentation.diceRoll.values.size),
            )
        }
    }

    /**
     * 直接重建可操作情境的目前牌牆、手牌、副露與牌河。
     *
     * @param result 已驗證的情境資料。
     * @param revealedTileIds 規則要求公開的牌張識別碼。
     */
    private fun publishStaticTable(result: DebugGameScenarioResult, revealedTileIds: Set<Uuid>) {
        val game = result.game
        val state = game.tableState
        publisher.publishWallStructure(
            gameId = game.id,
            assemblyStructure = result.wallStructure,
            layout = result.wallLayout,
            dealerSeatIndex = state.dealerIndex,
            deadWallTileIds = state.reservedWallTiles.mapTo(mutableSetOf()) { tile -> tile.id },
            openingDiceCounts = emptyList(),
            isNewOpening = false,
            revealedTileIds = revealedTileIds,
        )
        val module = moduleRegistry.getModule(state.config)
        state.players.forEachIndexed { seatIndex, player ->
            publisher.publishPlayerTilesUpdated(
                gameId = game.id,
                seatIndex = seatIndex,
                standingTileIds = player.hand.tiles.map { tile -> tile.id },
                drawnTileId = player.hand.lastDrawn?.id,
                melds = player.hand.melds.map { meld -> meld.toPresentation(state.config.revealsClosedKanTiles) },
                setAsideTileIds = module.setAsideTiles(player).map { tile -> tile.id },
            )
            publisher.publishDiscardPileUpdated(
                gameId = game.id,
                seatIndex = seatIndex,
                discardTileIds = player.discardPile.entries.map { entry -> entry.tile.id },
                sidewaysMarkedTileId = (player.discardPile as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId(),
            )
        }
    }

    /**
     * 發布靜態情境與完整開局共用的桌上物件及局況資訊。
     *
     * @param result 提供目前權威桌況的情境結果。
     */
    private fun publishTableInformation(result: DebugGameScenarioResult) {
        val game = result.game
        val state = game.tableState
        publisher.publishRuleStateUpdated(game.id)
        publisher.publishRoundInfoUpdated(game.id, state)
    }
}
