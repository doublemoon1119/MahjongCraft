package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.SettlementExtrasOccasion
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.RoundConclusion
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import kotlin.uuid.Uuid

/**
 * 套用榮和結算：更新贏家與放銃者（或包牌責任者）的分數、記錄各贏家的 [GameAction.Ron] 動作歷史、清除反應視窗。
 *
 * - 手牌到此結束。不套用副露、不標記捨牌已被鳴走、不推進 `currentPlayerIndex`、不清除一發，這些都與吃／碰／槓不同。
 * - 一般捨牌榮和與搶槓榮和（[RespondToRobbingUseCase]）共用這段邏輯。差異只在誰是放銃者，以及是否計入搶槓役種：
 *   一般捨牌是真正的放銃者，搶槓則是暗槓／加槓的宣告者。
 * - `winnerIds` 可能不只一人（一炮多響且規則設定為多家和）。每位贏家各自呼叫 [MahjongRuleModule.declareRon] 獨立結算，
 *   再把金額加總到同一份分數異動。同一位玩家可能同時是某人的贏家、又是另一人的包牌責任者，金額疊加而不覆蓋。
 * - 場上供託（例如立直棒）與連莊點數（例如本場）的歸屬交給 [MahjongRuleModule.allocateSettlementExtras]。
 *   候選贏家依頭跳順位（從放銃者的下家起算）排列，規則可以只給最近的贏家、給每位贏家或自行分配。
 */
internal object RonSettlementResolver {
    /**
     * 榮和後桌況與每位贏家的完整算役結果。
     *
     * @property tableState 榮和結算後的桌況。反應視窗是否清除由呼叫端決定。
     * @property resolutions 各贏家的規則結算結果。
     */
    data class Result(val tableState: TableState, val resolutions: Map<Uuid, WinResolutionResult>)

    /**
     * @param state 目前的桌況（尚未套用本次榮和結算）。
     * @param players 目前的玩家列表（可能已套用本次回應者的過水/振聽等變化，尚未套用榮和結算）。
     * @param discarderId 放銃者 Uuid（一般捨牌榮和為真正的放銃者。搶槓榮和為暗槓/加槓的宣告者）。
     * @param winningTile 被榮和的那張牌。
     * @param module 該對局採用的規則模組。
     * @param winnerIds 本次榮和的贏家 Uuid 集合。
     * @param isRobbingKan 這次榮和是否為搶槓（搶加槓），透傳給 [MahjongRuleModule.declareRon]。
     * @return 套用榮和結算後的新 [TableState]（`pendingReaction`/`pendingRobbingReaction` 皆不動，由呼叫端
     *         視回傳值清除）。已驗證的和牌缺少結算時中斷交易，由協調器中止對局，不保留已完成的反應視窗。
     * @throws RuleContractViolationException 規則提供和牌資格卻回傳 null 結算。
     */
    fun resolve(
        state: TableState,
        players: List<MahjongPlayer>,
        discarderId: Uuid,
        winningTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
        winnerIds: Set<Uuid>,
        isRobbingKan: Boolean = false,
    ): Result {
        val settlements = winnerIds.associateWith { winnerId ->
            val winner = players.first { it.id == winnerId }
            module.declareRon(state, winner, winningTile, discarderId, isRobbingKan)
                ?: throw RuleContractViolationException(
                    reasonId = GameAbortReasonIds.MISSING_WIN_SETTLEMENT,
                    diagnostic = "Rule ${module.id} offered ron but returned no settlement for $winnerId",
                )
        }

        val scoreDeltas = mutableMapOf<Uuid, Int>()
        settlements.forEach { (winnerId, settlement) ->
            scoreDeltas[winnerId] = (scoreDeltas[winnerId] ?: 0) + settlement.totalGained
            settlement.paymentsByPlayerId.forEach { (payerId, amount) ->
                scoreDeltas[payerId] = (scoreDeltas[payerId] ?: 0) - amount
            }
        }

        val extras = SettlementExtrasResolver.resolve(
            state = state,
            module = module,
            occasion = SettlementExtrasOccasion.WIN,
            candidatePlayerIds = state.playersInTurnOrderFrom(discarderId, winnerIds),
            discarderId = discarderId,
            resolutions = settlements,
        )
        extras.scoreDeltas.forEach { (playerId, delta) -> scoreDeltas[playerId] = (scoreDeltas[playerId] ?: 0) + delta }

        val updatedPlayers = players.map { p ->
            val updated = p.copy(score = p.score + (scoreDeltas[p.id] ?: 0))
            if (p.id in winnerIds) updated.recordAction(GameAction.Ron(winningTile.id)) else updated
        }

        return Result(
            tableState = state.copy(
                players = updatedPlayers,
                components = extras.components,
                turnPhase = TurnPhase.RoundOver(RoundConclusion.Win(winnerIds.toSet())),
            ),
            resolutions = settlements,
        )
    }
}
