package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealContext
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealDecision
import kotlin.uuid.Uuid

/** 將規則提供的牌牆公開決策驗證後，原子套用至候選桌況。 */
internal object WallRevealDecisionApplier {
    /** 牌牆公開決策的 Flow 套用結果。 */
    sealed interface Result {
        /**
         * 決策已成功套用。
         *
         * @property tableState 套用規則動態狀態後的桌況。
         * @property newlyRevealedTileIds 本次新成為公開資訊的牌張 ID。
         */
        data class Applied(
            val tableState: TableState,
            val newlyRevealedTileIds: Set<Uuid> = emptySet(),
        ) : Result

        /**
         * 規則拒絕更新，或回傳的結果未通過 Flow 邊界驗證。
         *
         * @property reasonId 完整 namespaced 原因 ID。
         */
        data class Rejected(val reasonId: String) : Result
    }

    /** 呼叫規則 policy 並驗證其動態狀態及新增公開牌集合。 */
    fun apply(
        tableState: TableState,
        checkpoint: WallRevealCheckpoint,
        module: MahjongRuleModule<*>,
        actorPlayerId: Uuid? = null,
        sourceAction: GameAction? = null,
    ): Result {
        val decision = module.createWallRevealPolicy().resolve(
            WallRevealContext(checkpoint, tableState, actorPlayerId, sourceAction),
        )
        return when (decision) {
            WallRevealDecision.NoChange -> Result.Applied(tableState)
            is WallRevealDecision.Rejected -> Result.Rejected(decision.reasonId)
            is WallRevealDecision.Updated -> applyUpdatedDecision(tableState, decision)
        }
    }

    /** 驗證 policy 宣告的新增集合等於動態狀態實際新增的可見牌集合。 */
    internal fun applyUpdatedDecision(
        tableState: TableState,
        decision: WallRevealDecision.Updated,
    ): Result {
        val updatedState = tableState.copy(dynamicRuleState = decision.dynamicRuleState)
        val allWallTileIds = (updatedState.tileWall.getAllTiles() + updatedState.reservedWallTiles)
            .mapTo(mutableSetOf()) { it.id }
        if (!allWallTileIds.containsAll(decision.newlyRevealedTileIds)) {
            return Result.Rejected(INVALID_RESULT_REASON_ID)
        }

        val beforeVisible = (tableState.dynamicRuleState as? TileWallRevealable)
            ?.getVisibleTileIds(tableState)
            .orEmpty()
        val afterVisible = (updatedState.dynamicRuleState as? TileWallRevealable)
            ?.getVisibleTileIds(updatedState)
            .orEmpty()
        if ((beforeVisible - afterVisible).isNotEmpty() ||
            afterVisible - beforeVisible != decision.newlyRevealedTileIds
        ) {
            return Result.Rejected(INVALID_RESULT_REASON_ID)
        }

        return Result.Applied(updatedState, decision.newlyRevealedTileIds)
    }

    /** Policy 結果未通過 Flow 公開牌引用或可見差集驗證。 */
    const val INVALID_RESULT_REASON_ID = "mahjongcraft:invalid_wall_reveal_result"
}
