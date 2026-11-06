package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.bundled.BundledTaiwanExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionWinOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanSpecialFlowerWinDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionWin
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
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
    /**
     * 七搶一的演出與結算應使用規則提供的結果、成立花牌與責任玩家，不呈現成一般自摸；不攤牌，結算只列出自己的七張花與
     * 被搶的那張。
     */
    @Test
    fun `stage presents special win identity and responsibility`() {
        val flowers = TaiwanTileTypes.ALL.map { FakeIdentifiedTileFactory.create(Tile.Extension(it)) }
        val ownFlowers = flowers.dropLast(1)
        val robbedFlower = flowers.last()
        val winner = FakeMahjongPlayerFactory.create().let { it.copy(tileAreas = it.tileAreas.withArea(TaiwanPlayerTileAreaIds.FLOWERS, ownFlowers)) }
        val payer = FakeMahjongPlayerFactory.create().let { it.copy(tileAreas = it.tileAreas.withArea(TaiwanPlayerTileAreaIds.FLOWERS, listOf(robbedFlower))) }
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
        val winningFlowerId = robbedFlower.id
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
                    revealsWinnerHands = false,
                ),
                scoreDeltas = current.players.associate { player ->
                    val oldScore = previous.players.first { it.id == player.id }.score
                    player.id to (player.score - oldScore)
                },
                extensionDetail = TaiwanSpecialFlowerWinDetail(
                    taiTypeId = TaiwanTaiType.SEVEN_FLOWERS.id,
                    tai = TaiwanTaiType.SEVEN_FLOWERS.tai,
                    winningFlowerId = winningFlowerId,
                ),
            ),
        )

        val staged = assertNotNull(handoff.take(previous.id, setOf(winner.id)))
        assertEquals(winningFlowerId, staged.celebration.winningTileId)
        assertEquals(TaiwanRoundOutcomeIds.SEVEN_FLOWERS, staged.settlement.outcomeId)
        assertFalse(staged.celebration.isTsumo)
        assertFalse(staged.celebration.revealsWinnerHands)
        val winnerPresentation = staged.settlement.winners.single()
        assertEquals(emptyList(), winnerPresentation.standingTileIds)
        val fields = winnerPresentation.detailFields.associate { it.id to it.value }
        assertEquals(
            WinSettlementDetailValue.TileFaces(ownFlowers.map { TileFaceView(it.tile) }),
            fields[TaiwanWinSettlementIds.FLOWERS_FIELD],
        )
        assertEquals(WinSettlementDetailValue.TileFaces(listOf(TileFaceView(robbedFlower.tile))), fields[TaiwanWinSettlementIds.WINNING_FLOWER_FIELD])
    }
}
