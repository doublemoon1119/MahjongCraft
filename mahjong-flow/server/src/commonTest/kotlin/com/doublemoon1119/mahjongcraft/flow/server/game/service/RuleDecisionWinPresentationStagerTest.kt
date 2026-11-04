package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.bundled.BundledTaiwanExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionWinOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanSpecialFlowerWinDetail
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionWin
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** [RuleDecisionWinPresentationStager] 的特殊花胡交接測試。 */
class RuleDecisionWinPresentationStagerTest {
    /** 七搶一的演出與結算應使用規則提供的結果、成立花牌與責任玩家，不呈現成一般自摸。 */
    @Test
    fun `stage presents special win identity and responsibility`() {
        val winner = FakeMahjongPlayerFactory.create()
        val payer = FakeMahjongPlayerFactory.create()
        val others = List(2) { FakeMahjongPlayerFactory.create() }
        val previous = FakeTableStateFactory.create(
            players = listOf(winner, payer) + others,
            config = TaiwanRuleConfig(),
        )
        val current = previous.copy(
            players = previous.players.map { player ->
                when (player.id) {
                    winner.id -> player.copy(score = player.score + 8)
                    payer.id -> player.copy(score = player.score - 8)
                    else -> player
                }
            },
        )
        val winningFlowerId = FakeIdentifiedTileFactory.create(Tile.Extension(TaiwanTileTypes.SPRING)).id
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val detailResolvers = WinSettlementDetailResolverRegistry(bundledCoreExtensionRegistries().componentRegistry).apply {
            BundledTaiwanExtension.registerWinSettlementDetailResolvers(this)
            freeze()
        }
        val handoff = WinPresentationHandoff()
        val stager = RuleDecisionWinPresentationStager(handoff, detailResolvers)

        stager.stage(
            gameId = previous.id,
            previousState = previous,
            currentState = current,
            module = moduleRegistry.getModule(current.config),
            aiPlayerIds = emptySet(),
            win = RuleDecisionWin(
                outcome = RuleDecisionWinOutcome(
                    outcomeId = TaiwanRoundOutcomeIds.SEVEN_FLOWERS,
                    winnerPlayerIds = setOf(winner.id),
                    responsiblePlayerIds = setOf(payer.id),
                    winningTileId = winningFlowerId,
                ),
                scoreDeltas = current.players.associate { player ->
                    val oldScore = previous.players.first { it.id == player.id }.score
                    player.id to (player.score - oldScore)
                },
                extensionDetail = TaiwanSpecialFlowerWinDetail(
                    taiTypeId = TaiwanTaiType.SEVEN_FLOWERS.id,
                    tai = TaiwanTaiType.SEVEN_FLOWERS.tai,
                ),
            ),
        )

        val staged = assertNotNull(handoff.take(previous.id, setOf(winner.id)))
        assertEquals(winningFlowerId, staged.celebration.winningTileId)
        assertEquals(TaiwanRoundOutcomeIds.SEVEN_FLOWERS, staged.settlement.outcomeId)
        assertFalse(staged.celebration.isTsumo)
    }
}
