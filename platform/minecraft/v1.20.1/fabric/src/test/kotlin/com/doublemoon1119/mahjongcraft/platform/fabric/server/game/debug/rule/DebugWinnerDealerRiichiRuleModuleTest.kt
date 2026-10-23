package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionContext
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionDecision
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.DebugMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 驗證開發用和牌者坐莊、每局換位四人日麻的莊家去留、換位與設定格式。 */
class DebugWinnerDealerRiichiRuleModuleTest {
    /** 受測規則模組。 */
    private val module = DebugWinnerDealerRiichiRuleModule(
        DebugWinnerDealerRiichiRuleModule.ID,
        DebugWinnerDealerRiichiRuleConfig(RiichiRuleConfig(gameLength = RiichiGameLength.East)),
    )

    /** 東一局的四人桌，第一位是莊家。 */
    private val state: TableState = FakeTableStateFactory.create(
        players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(it) },
        config = module.config,
    )

    /** 子家和牌時由和牌者坐莊；多人和牌時取放銃者之後最近的和牌者。 */
    @Test
    fun `a non dealer winner takes the deal`() {
        val single = module.resolveRoundTransition(winContext(setOf(state.players[2].id), discarderId = state.players[0].id))
        val double = module.resolveRoundTransition(winContext(setOf(state.players[1].id, state.players[3].id), discarderId = state.players[2].id))

        assertEquals(RoundTransitionDirective.PassTo(state.players[2].id), single)
        assertEquals(RoundTransitionDirective.PassTo(state.players[3].id), double)
    }

    /** 莊家和牌與流局沿用日麻的連莊與過莊。 */
    @Test
    fun `a dealer win and draws keep riichi rules`() {
        val dealerWin = module.resolveRoundTransition(winContext(setOf(state.players[0].id), discarderId = state.players[1].id))
        val notenDraw = module.resolveRoundTransition(
            RoundTransitionContext(
                tableState = state,
                outcomeId = RiichiExhaustiveDrawReason.Normal.id,
                classification = RoundCompletionClassification.EXHAUSTIVE_DRAW,
                beneficiaryPlayerIds = setOf(state.players[1].id),
                responsiblePlayerIds = emptySet(),
                exhaustiveDrawReason = RiichiExhaustiveDrawReason.Normal,
            ),
        )

        assertEquals(RoundTransitionDirective.RepeatDealer, dealerWin)
        assertEquals(RoundTransitionDirective.PassToNextSeat, notenDraw)
    }

    /** 每次繼續對局都讓所有玩家往下一個座位輪換一格。 */
    @Test
    fun `every next round rotates the seats by one`() {
        val decision = module.createMatchProgressionPolicy().decide(
            MatchProgressionContext(
                tableState = state,
                completion = RoundCompletionSummary(
                    outcomeId = BuiltInRoundOutcomeIds.RON,
                    classification = RoundCompletionClassification.WIN,
                    beneficiaryPlayerIds = setOf(state.players[2].id),
                    transitionDirective = RoundTransitionDirective.PassTo(state.players[2].id),
                    settledScoresByPlayerId = state.players.associate { it.id to it.score },
                ),
                rankedPlayerIds = state.players.map { it.id },
            ),
        )

        val ids = state.players.map { it.id }
        assertEquals(ids.drop(1) + ids.first(), assertIs<MatchProgressionDecision.ContinueMatch>(decision).seatOrder)
    }

    /** 規則設定可以經網路格式與存檔格式往返。 */
    @Test
    fun `the config round trips through network and persistence formats`() {
        val config = DebugWinnerDealerRiichiRuleConfig(RiichiRuleConfig(redDoraCount = 0, useLocalYaku = true))
        val debugExtension = DebugMahjongExtension(winRoundContinuationState = DebugWinRoundContinuationState())
        val networkRegistries = DefaultNetworkDtoRegistries().apply {
            registerBundledNetworkDtos()
            debugExtension.registerNetworkDtos(this)
            freeze()
        }
        val format = NetworkJson(networkRegistries)
        val serializer = PolymorphicSerializer(MahjongRuleConfigDto::class)
        val encoded = format.encodeToString(serializer, networkRegistries.ruleConfig.toDto(config))

        assertEquals(config, networkRegistries.ruleConfig.toDomain(format.decodeFromString(serializer, encoded)))
        val persistenceRegistries = bundledPersistenceRegistries().apply {
            debugExtension.registerPersistenceDtos(this)
            freeze()
        }
        assertEquals(config, persistenceRegistries.ruleConfigs.decode(persistenceRegistries.ruleConfigs.encode(config)))
    }

    /**
     * 建立榮和結束的情境。
     *
     * @param winnerIds 和牌者。
     * @param discarderId 放銃者。
     * @return 本局結果情境。
     */
    private fun winContext(winnerIds: Set<Uuid>, discarderId: Uuid): RoundTransitionContext = RoundTransitionContext(
        tableState = state,
        outcomeId = BuiltInRoundOutcomeIds.RON,
        classification = RoundCompletionClassification.WIN,
        beneficiaryPlayerIds = winnerIds,
        responsiblePlayerIds = setOf(discarderId),
    )
}
