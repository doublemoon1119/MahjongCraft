package com.doublemoon1119.mahjongcraft.example.decision

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolver
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 簡易配對麻將的局前換牌流程。
 *
 * 每位玩家從起手牌中選兩張，所有人提交後才一次把牌交給固定座位順序的下一位玩家。
 * 簡易配對規則設定未啟用換牌時，不建立任何準備步驟。
 */
class ExamplePairingRuleDecisionResolver : RuleDecisionResolver {
    override val ruleModuleId: String = ExamplePairingIds.RULE_MODULE

    override fun ruleDecisionConfig(ruleConfig: MahjongRuleConfig): RuleDecisionConfig? = RuleDecisionConfig(EXCHANGE_SECONDS).takeIf { isExchangeEnabled(ruleConfig) }

    override fun begin(tableState: TableState, ruleModule: MahjongRuleModule<*>): PendingRuleDecision? {
        if (!isExchangeEnabled(ruleModule.config)) return null
        val inputSpecs = tableState.players.associate { player ->
            player.id to RuleDecisionInputSpec.TileSelection(
                eligibleTileIds = player.hand.standingTiles.mapTo(linkedSetOf()) { it.id },
                minCount = EXCHANGE_TILE_COUNT,
                maxCount = EXCHANGE_TILE_COUNT,
            )
        }
        return PendingRuleDecision(ExamplePairingIds.EXCHANGE_STEP, 0, inputSpecs)
    }

    override fun accepts(
        tableState: TableState,
        ruleDecision: PendingRuleDecision,
        playerId: Uuid,
        submission: RuleDecisionSubmission,
        ruleModule: MahjongRuleModule<*>,
    ): Boolean {
        if (!isExchangeEnabled(ruleModule.config)) return false
        val selection = submission as? RuleDecisionSubmission.Tiles ?: return false
        val input = ruleDecision.inputSpecsByPlayerId[playerId] as? RuleDecisionInputSpec.TileSelection
            ?: return false
        return selection.tileIds.size == EXCHANGE_TILE_COUNT && selection.tileIds.all { it in input.eligibleTileIds }
    }

    override fun fallbackSubmission(
        tableState: TableState,
        ruleDecision: PendingRuleDecision,
        playerId: Uuid,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionSubmission {
        val input = ruleDecision.inputSpecsByPlayerId.getValue(playerId) as RuleDecisionInputSpec.TileSelection
        return RuleDecisionSubmission.Tiles(input.eligibleTileIds.sortedBy(Uuid::toString).take(EXCHANGE_TILE_COUNT).toSet())
    }

    override fun resolve(
        tableState: TableState,
        ruleDecision: PendingRuleDecision,
        ruleModule: MahjongRuleModule<*>,
    ): RuleDecisionResolution {
        require(isExchangeEnabled(ruleModule.config)) { "Pairing exchange is disabled" }
        require(ruleDecision.isComplete) { "Pairing exchange requires every player to submit" }
        val players = tableState.players
        val selectedByPlayer = ruleDecision.submissionsByPlayerId.mapValues { (_, submission) ->
            (submission as? RuleDecisionSubmission.Tiles)?.tileIds
                ?: error("Pairing exchange requires tile selections")
        }
        val updatedPlayers = players.mapIndexed { index, player ->
            val sourcePlayer = players[(index - 1 + players.size) % players.size]
            val selectedIds = selectedByPlayer.getValue(player.id)
            val incomingIds = selectedByPlayer.getValue(sourcePlayer.id)
            val standing = player.hand.standingTiles
            require(selectedIds.all { id -> standing.any { it.id == id } }) {
                "Exchange selected a tile not owned by player"
            }
            val incoming = sourcePlayer.hand.standingTiles.filter { it.id in incomingIds }
            require(incoming.size == EXCHANGE_TILE_COUNT) { "Exchange source must provide exactly two tiles" }
            player.copy(hand = player.hand.copy(tiles = standing.filterNot { it.id in selectedIds } + incoming, lastDrawn = null))
        }
        return RuleDecisionResolution(tableState.copy(players = updatedPlayers), nextStep = null)
    }

    private companion object {
        /** 換牌步驟的倒數秒數。 */
        const val EXCHANGE_SECONDS = 30

        /** 每位玩家本步驟必須選出的牌張數。 */
        const val EXCHANGE_TILE_COUNT = 2

        /**
         * 判斷設定是否屬於簡易配對規則且啟用換牌。
         *
         * @param ruleConfig 要檢查的規則設定。
         * @return 僅簡易配對規則且啟用換牌時為 true。
         */
        fun isExchangeEnabled(ruleConfig: MahjongRuleConfig): Boolean = (ruleConfig as? ExamplePairingRuleConfig)?.exchangeEnabled == true
    }
}
