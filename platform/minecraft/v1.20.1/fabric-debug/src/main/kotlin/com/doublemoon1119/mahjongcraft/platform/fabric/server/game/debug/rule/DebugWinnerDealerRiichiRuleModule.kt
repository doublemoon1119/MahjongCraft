package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionContext
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionDecision
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionPolicy
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 只在開發環境登記的四人日麻變體：子家和牌時由和牌者坐莊，每局結束後所有玩家往下一個座位輪換一格，其餘規則與
 * [RiichiRuleModule] 相同。
 *
 * 用來在遊戲內驗證規則指定下一任莊家與局間換位的流程與呈現。多人和牌時取放銃者之後最近的和牌者；
 * 莊家和牌或其他結果沿用日麻的連莊與過莊。除了這兩項以外的成員都交給以 [DebugWinnerDealerRiichiRuleConfig.base]
 * 建立的四人日麻模組。
 *
 * @property id 規則模組 ID。
 * @property config 開發用規則配置。
 * @property delegate 提供其餘規則的四人日麻模組。
 */
internal class DebugWinnerDealerRiichiRuleModule private constructor(
    override val id: String,
    override val config: DebugWinnerDealerRiichiRuleConfig,
    private val delegate: RiichiFamilyRuleModule<DebugWinnerDealerRiichiRuleConfig>,
) : RiichiFamilyRuleModule<DebugWinnerDealerRiichiRuleConfig> by delegate {
    /**
     * 以 [DebugWinnerDealerRiichiRuleConfig.base] 建立委派的四人日麻模組。
     *
     * @param id 規則模組 ID。
     * @param config 開發用規則配置。
     */
    constructor(id: String, config: DebugWinnerDealerRiichiRuleConfig) : this(id, config, riichiDelegate(id, config))

    override fun resolveRoundTransition(context: RoundTransitionContext): RoundTransitionDirective {
        val state = context.tableState
        val winnerIds = context.beneficiaryPlayerIds
        if (context.classification != RoundCompletionClassification.WIN || winnerIds.isEmpty() || state.dealerPlayerId in winnerIds) {
            return delegate.resolveRoundTransition(context)
        }
        val from = context.responsiblePlayerIds.singleOrNull() ?: state.dealerPlayerId
        return RoundTransitionDirective.PassTo(state.playersInTurnOrderFrom(from, winnerIds).first())
    }

    override fun createMatchProgressionPolicy(): MatchProgressionPolicy {
        val base = delegate.createMatchProgressionPolicy()
        return object : MatchProgressionPolicy {
            override fun initialPosition(playerCount: Int) = base.initialPosition(playerCount)

            override fun decide(context: MatchProgressionContext): MatchProgressionDecision = when (val decision = base.decide(context)) {
                is MatchProgressionDecision.ContinueMatch -> {
                    val seatOrder = context.tableState.players.map { it.id }
                    decision.copy(seatOrder = seatOrder.drop(1) + seatOrder.first())
                }

                is MatchProgressionDecision.EndMatch -> decision
            }
        }
    }

    companion object {
        /** 開發用和牌者坐莊、每局換位四人日麻的規則模組 ID。 */
        val ID: String = MahjongCraftMetadata.id("debug_winner_dealer_riichi")
    }
}

/**
 * 建立 [DebugWinnerDealerRiichiRuleModule] 委派的四人日麻模組。
 *
 * 規則模組介面只有 `config` 使用型別參數，而 [DebugWinnerDealerRiichiRuleModule] 自行提供 `config`，因此委派對象的型別參數
 * 不會被讀到，轉型不影響執行。
 *
 * @param id 規則模組 ID。
 * @param config 開發用規則配置。
 * @return 以 [DebugWinnerDealerRiichiRuleConfig.base] 建立的四人日麻模組。
 */
@Suppress("UNCHECKED_CAST")
private fun riichiDelegate(
    id: String,
    config: DebugWinnerDealerRiichiRuleConfig,
): RiichiFamilyRuleModule<DebugWinnerDealerRiichiRuleConfig> = RiichiRuleModule(id, config.base) as RiichiFamilyRuleModule<DebugWinnerDealerRiichiRuleConfig>
