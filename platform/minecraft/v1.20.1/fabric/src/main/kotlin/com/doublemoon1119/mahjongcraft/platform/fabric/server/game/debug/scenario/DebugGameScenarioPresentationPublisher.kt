package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStep
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.config.dealBatchSizes
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import com.doublemoon1119.mahjongcraft.logic.tile.component.publicFaceUpWallTileIds
import kotlin.uuid.Uuid

/**
 * 將開發情境轉交給正式 [GamePresentationPublisher] 的發布器。
 *
 * @property publisher 發布牌牆、牌區、桌上物件與局況的正式呈現介面。
 * @property moduleRegistry 查詢牌張排序與移出手牌區等規則能力。
 * @property componentRegistry 正式組件與共同姿態效果。
 */
class DebugGameScenarioPresentationPublisher(
    private val publisher: GamePresentationPublisher,
    private val moduleRegistry: MahjongModuleRegistry,
    private val componentRegistry: ComponentRegistry,
) {
    /**
     * 依情境指定的呈現方式發布完整桌況。
     *
     * @param result 已通過情境驗證的對局與呈現資料。
     */
    fun publish(result: DebugGameScenarioResult) {
        val game = result.game
        val state = game.tableState
        val revealedTileIds = moduleRegistry.getModule(state.config).publicFaceUpWallTileIds(state, componentRegistry)
        publisher.clearPlayerTiles(game.id)
        when (val presentation = result.presentation) {
            DebugGameScenarioPresentation.StaticWall -> publishStaticTable(result, revealedTileIds)
            is DebugGameScenarioPresentation.InitialRound -> {
                publisher.publishWallStructure(
                    gameId = game.id,
                    assemblyStructure = result.wallStructure,
                    layout = result.wallLayout,
                    dealerSeatIndex = state.dealerIndex,
                    reservedWallTileIds = state.reservedWallTiles.mapTo(mutableSetOf()) { tile -> tile.id },
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
        (result.presentation as? DebugGameScenarioPresentation.InitialRound)?.let { presentation ->
            publisher.publishInitialDeal(
                gameId = game.id,
                tableState = state,
                handTileIdsBySeatIndex = presentation.dealOrderHandTileIdsBySeatIndex,
                dealBatchSizes = state.config.dealBatchSizes(),
                openingDiceCounts = listOf(presentation.diceRoll.values.size),
            )
        }
    }

    /**
     * 直接重建可操作情境的目前牌牆、手牌、副露、牌河與桌上物件。
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
            reservedWallTileIds = state.reservedWallTiles.mapTo(mutableSetOf()) { tile -> tile.id },
            openingDiceCounts = emptyList(),
            isNewOpening = false,
            revealedTileIds = revealedTileIds,
        )
        // 清除後沒有先前的呈現，牌、桌上物件、局況與玩家資訊直接擺成情境的桌況。
        publisher.publishTableSteps(game.id, listOf(TableStep(state, emptyList())))
    }
}
