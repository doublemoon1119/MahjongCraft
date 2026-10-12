package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Single

/**
 * 在寫入 repository 前集中驗證開發情境的權威狀態完整性。
 *
 * @property moduleRegistry 取得情境規則的牌山組成與移出手牌區能力。
 */
@Single
class DebugGameScenarioValidator(
    private val moduleRegistry: MahjongModuleRegistry,
) {
    /**
     * 驗證候選結果保留遊戲身分、玩家歸屬、牌張守恆與必要呈現資料。
     *
     * @param context 情境建立前的權威對局與發起者。
     * @param result 要寫入的候選情境。
     * @throws IllegalArgumentException 候選情境違反任何完整性條件時拋出。
     */
    fun validate(context: DebugGameScenarioContext, result: DebugGameScenarioResult) {
        val previous = context.currentGame
        val candidate = result.game
        require(candidate.id == previous.id) { "Debug scenario must preserve the game ID" }
        require(candidate.matchId == previous.matchId) { "Debug scenario must preserve the match ID" }
        require(candidate.tableState.players.map { it.id }.toSet() == previous.tableState.players.map { it.id }.toSet()) {
            "Debug scenario must preserve all game players"
        }
        require(candidate.roomPlayerIds == previous.roomPlayerIds) { "Debug scenario must preserve room player order" }
        require(candidate.hostId == previous.hostId) { "Debug scenario must preserve the host" }
        if (result.presentation == DebugGameScenarioPresentation.StaticWall) {
            val invokingPlayerHasAuthority = candidate.tableState.currentPlayer.id == context.invokingPlayerId ||
                candidate.tableState.pendingReaction?.let { pending ->
                    context.invokingPlayerId in pending.eligiblePlayerIds && context.invokingPlayerId !in pending.responses
                } == true ||
                candidate.tableState.pendingRobbingReaction?.let { pending ->
                    context.invokingPlayerId in pending.eligiblePlayerIds && context.invokingPlayerId !in pending.responses
                } == true
            require(invokingPlayerHasAuthority) {
                "Static debug scenario must give the invoking player decision authority"
            }
        }

        val module = moduleRegistry.getModule(candidate.tableState.config)
        val allTiles = candidate.tableState.allTiles(module)
        require(allTiles.map { it.id }.distinct().size == allTiles.size) { "Debug scenario contains duplicate tile UUIDs" }
        require(result.wallStructure.keys == allTiles.mapTo(mutableSetOf()) { it.id }) {
            "Debug scenario wall structure must contain every tile UUID exactly once"
        }
        require(result.wallLayout.placements.keys == result.wallStructure.keys) {
            "Debug scenario presentation layout must contain every wall structure UUID exactly once"
        }
        requireNotNull(candidate.tableState.physicalWallLayout) {
            "Debug scenario must provide the current authoritative physical wall layout"
        }
        (result.presentation as? DebugGameScenarioPresentation.InitialRound)?.let { presentation ->
            validateInitialRoundPresentation(candidate.tableState, presentation)
        }

        val expectedCounts = module.createWallFactory().create().getAllTiles().groupingBy { it.tile }.eachCount()
        val actualCounts = allTiles.groupingBy { it.tile }.eachCount()
        require(actualCounts == expectedCounts) { "Debug scenario tile multiset does not match its rule config" }

        val playerIds = candidate.tableState.players.mapTo(mutableSetOf()) { it.id }
        candidate.tableState.pendingReaction?.let { pending ->
            require(pending.discarderId in playerIds) { "Pending discard reaction references an unknown player" }
            require((pending.eligiblePlayerIds + pending.responses.keys).all { it in playerIds }) {
                "Pending discard reaction references an unknown player"
            }
            require(pending.tileId in allTiles.mapTo(mutableSetOf()) { it.id }) {
                "Pending discard reaction references an unknown tile"
            }
        }
        candidate.tableState.pendingRobbingReaction?.let { pending ->
            require(pending.declarerId in playerIds) { "Pending robbing reaction references an unknown player" }
            require((pending.eligiblePlayerIds + pending.responses.keys).all { it in playerIds }) {
                "Pending robbing reaction references an unknown player"
            }
            require(pending.robbedTile.id in allTiles.mapTo(mutableSetOf()) { it.id }) {
                "Pending robbing reaction references an unknown tile"
            }
        }
    }

    /**
     * 收集權威桌況目前持有的全部實體牌。
     *
     * @param module 提供移出手牌區牌張的規則模組。
     * @return 所有手牌、副露、牌河、移出手牌區、牌山與保留區的牌。
     */
    private fun TableState.allTiles(module: MahjongRuleModule<*>): List<IdentifiedTile> = players.flatMap { player ->
        player.hand.allTiles + player.discardPile.entries.map { entry -> entry.tile } + module.setAsideTiles(player)
    } + tileWall.getAllTiles() + reservedWallTiles

    /**
     * 驗證開局發牌清單涵蓋權威手牌，翻牌後順序與權威手牌完全一致。
     *
     * @param state 候選權威桌況。
     * @param presentation 要驗證的開局呈現資料。
     * @throws IllegalArgumentException 骰子、座位或手牌內容不符合要求時拋出。
     */
    private fun validateInitialRoundPresentation(
        state: TableState,
        presentation: DebugGameScenarioPresentation.InitialRound,
    ) {
        require(presentation.diceRoll.values.isNotEmpty()) { "Initial round presentation must provide dice" }
        val expectedSeatIndices = state.players.indices.toSet()
        require(presentation.dealOrderHandTileIdsBySeatIndex.keys == expectedSeatIndices) {
            "Initial round deal order must contain every seat"
        }
        require(presentation.postFlipHandTileIdsBySeatIndex.keys == expectedSeatIndices) {
            "Initial round final hands must contain every seat"
        }
        state.players.forEachIndexed { seatIndex, player ->
            val authoritativeIds = player.hand.tiles.map { tile -> tile.id }
            val dealOrderIds = presentation.dealOrderHandTileIdsBySeatIndex.getValue(seatIndex)
            val postFlipIds = presentation.postFlipHandTileIdsBySeatIndex.getValue(seatIndex)
            require(dealOrderIds.toSet() == authoritativeIds.toSet() && dealOrderIds.size == authoritativeIds.size) {
                "Initial round deal order does not match the authoritative hand at seat $seatIndex"
            }
            require(postFlipIds == authoritativeIds) {
                "Initial round final hand order does not match the authoritative hand at seat $seatIndex"
            }
        }
    }
}
