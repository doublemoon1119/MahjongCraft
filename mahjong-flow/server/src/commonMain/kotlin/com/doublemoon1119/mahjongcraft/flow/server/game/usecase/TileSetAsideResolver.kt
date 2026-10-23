package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.recordAbortiveDraw
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.PendingFollowUpDraw
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 決定一次移出手牌動作的去向：開啟搶和視窗、因一炮多響判定流局，或直接套用並補牌。
 *
 * 玩家自行宣告與摸牌後的自動處理共用這個判定；宣告的合法性由呼叫端先檢查。
 */
internal object TileSetAsideResolver {
    /** 一次移出手牌動作的去向。 */
    sealed interface Resolution {
        /** 這次要移出手牌的牌。 */
        val setAsideTile: IdentifiedTile

        /**
         * 其他玩家可以用移出的牌榮和，已開啟搶和視窗；動作暫緩套用。
         *
         * @property tableState 開啟搶和視窗後的權威桌況。
         * @property setAsideTile 這次要移出手牌的牌。
         * @property declaredState 牌已移出、尚未補牌的候選桌況，只供呈現使用。
         */
        data class RobbingWindowOpened(
            val tableState: TableState,
            override val setAsideTile: IdentifiedTile,
            val declaredState: TableState?,
        ) : Resolution

        /**
         * 多位玩家可以榮和，依一炮多響設定判定流局；這次動作視為未成立。
         *
         * @property tableState 已記錄流局的權威桌況。
         * @property setAsideTile 這次要移出手牌的牌。
         * @property reason 流局原因。
         */
        data class AbortiveDraw(
            val tableState: TableState,
            override val setAsideTile: IdentifiedTile,
            val reason: ExhaustiveDrawReason,
        ) : Resolution

        /**
         * 沒有人可以搶，動作與補牌已套用。
         *
         * @property applied 套用與補牌的結果。
         * @property setAsideTile 這次移出手牌的牌。
         */
        data class Applied(
            val applied: SelfDeclarationApplier.Result.Applied,
            override val setAsideTile: IdentifiedTile,
        ) : Resolution

        /**
         * 規則拒絕補牌或回傳無法套用的結果；權威桌況不變。
         *
         * @property reasonId 完整 namespaced 原因 ID。
         * @property setAsideTile 這次要移出手牌的牌。
         */
        data class Rejected(
            val reasonId: String,
            override val setAsideTile: IdentifiedTile,
        ) : Resolution
    }

    /**
     * 判定並套用一次移出手牌動作。
     *
     * @param state 尚未套用這次動作的權威桌況。
     * @param playerId 執行動作的玩家。
     * @param action 移出手牌的擴充動作。
     * @param setAsideTile 規則指定要移出的牌。
     * @param module 對局採用的規則模組。
     * @param pendingFollowUpDraws 摸牌後自動處理還沒詢問過規則的牌與來源；開啟搶和視窗時保存在視窗中，放過後繼續詢問。
     * @return 這次動作的去向。
     */
    fun resolve(
        state: TableState,
        playerId: Uuid,
        action: GameAction.Extension,
        setAsideTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
        pendingFollowUpDraws: List<PendingFollowUpDraw> = emptyList(),
    ): Resolution {
        RuleHookResultValidator.requireHeldSetAsideTile(state, playerId, action, setAsideTile)
        val ronEligiblePlayerIds = RobbingEligibility.ronEligiblePlayerIds(
            tableState = state,
            declarerId = playerId,
            declaredAction = action,
            robbedTile = setAsideTile,
            module = module,
        )
        val ronResolution = when (ronEligiblePlayerIds.size) {
            0, 1 -> null
            2 -> state.config.multiRonPolicy.doubleRonResolution
            else -> state.config.multiRonPolicy.tripleRonResolution
        }
        val ronWinningPlayerIds = when (ronResolution) {
            null, RonResolution.ALL_WINNERS -> ronEligiblePlayerIds
            RonResolution.NEAREST_WINNER -> setOf(state.nearestPlayerInTurnOrder(playerId, ronEligiblePlayerIds))
            RonResolution.ABORTIVE_DRAW -> emptySet()
        }
        val abortiveDrawReason = if (ronResolution == RonResolution.ABORTIVE_DRAW) RuleHookResultValidator.requireMultiRonAbortiveDrawReason(module) else null
        if (abortiveDrawReason != null) {
            return Resolution.AbortiveDraw(
                tableState = state.recordAbortiveDraw(abortiveDrawReason, declarerId = null, module = module),
                setAsideTile = setAsideTile,
                reason = abortiveDrawReason,
            )
        }
        if (ronWinningPlayerIds.isNotEmpty()) {
            return Resolution.RobbingWindowOpened(
                tableState = state.copy(
                    pendingRobbingReaction = PendingRobbingReaction(
                        declarerId = playerId,
                        declaredAction = action,
                        robbedTile = setAsideTile,
                        eligiblePlayerIds = ronWinningPlayerIds,
                        pendingFollowUpDraws = pendingFollowUpDraws,
                    ),
                    revealedHandTileIds = state.revealedHandTileIds + setAsideTile.id,
                ),
                setAsideTile = setAsideTile,
                declaredState = SelfDeclarationApplier.declaredState(state, playerId, action, setAsideTile, module),
            )
        }
        return when (val applied = SelfDeclarationApplier.applyTileSetAside(state, playerId, action, module)) {
            is SelfDeclarationApplier.Result.Applied -> Resolution.Applied(applied, setAsideTile)
            is SelfDeclarationApplier.Result.Rejected -> Resolution.Rejected(applied.reasonId, setAsideTile)
        }
    }
}
