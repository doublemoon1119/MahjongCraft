package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Single

/** 在寫入 repository 前集中驗證 debug 情境的權威狀態完整性。 */
@Single
class DebugGameScenarioValidator(
    private val moduleRegistry: MahjongModuleRegistry,
) {
    /** 驗證候選結果沒有改變遊戲身分、玩家歸屬或牌張守恆。 */
    fun validate(context: DebugGameScenarioContext, result: DebugGameScenarioResult) {
        val previous = context.currentGame
        val candidate = result.game
        require(candidate.id == previous.id) { "Debug scenario must preserve the game ID" }
        require(candidate.tableState.players.map { it.id }.toSet() == previous.tableState.players.map { it.id }.toSet()) {
            "Debug scenario must preserve all game players"
        }
        require(candidate.roomPlayerIds == previous.roomPlayerIds) { "Debug scenario must preserve room player order" }
        require(candidate.hostId == previous.hostId) { "Debug scenario must preserve the host" }
        require(candidate.tableState.currentPlayer.id == context.invokingPlayerId) {
            "Debug scenario must give the invoking player decision authority"
        }

        val allTiles = candidate.tableState.allTiles()
        require(allTiles.map { it.id }.distinct().size == allTiles.size) { "Debug scenario contains duplicate tile UUIDs" }
        require(result.wallStructure.keys == allTiles.mapTo(mutableSetOf()) { it.id }) {
            "Debug scenario wall structure must contain every tile UUID exactly once"
        }

        val module = moduleRegistry.getModule(candidate.tableState.config)
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
        candidate.tableState.pendingKanReaction?.let { pending ->
            require(pending.declarerId in playerIds) { "Pending kan reaction references an unknown player" }
            require((pending.eligiblePlayerIds + pending.responses.keys).all { it in playerIds }) {
                "Pending kan reaction references an unknown player"
            }
            require(pending.robbedTile.id in allTiles.mapTo(mutableSetOf()) { it.id }) {
                "Pending kan reaction references an unknown tile"
            }
        }
    }

    /** 收集權威桌況目前持有的全部實體牌。 */
    private fun TableState.allTiles(): List<IdentifiedTile> = players.flatMap { it.hand.allTiles + it.discardPile.entries.map { entry -> entry.tile } } +
        tileWall.getAllTiles() + reservedWallTiles
}
