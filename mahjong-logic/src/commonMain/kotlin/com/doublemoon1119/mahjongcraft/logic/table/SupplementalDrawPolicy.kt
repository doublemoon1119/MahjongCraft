package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import kotlin.uuid.Uuid

/**
 * 動作完成後由規則決定是否補摸額外牌張的純邏輯 policy。
 *
 * Flow 只負責建立候選桌況、驗證結果及原子套用，不得自行假設補牌來源、數量或公開資訊。
 */
fun interface SupplementalDrawPolicy {
    /** 根據 [context] 決定本次動作完成後的補牌與牌牆變化。 */
    fun resolve(context: SupplementalDrawContext): SupplementalDrawDecision
}

/**
 * 補牌 policy 的完整輸入。
 *
 * @property tableStateBeforeAction 動作尚未套用前的權威桌況。
 * @property tableStateAfterAction 已套用動作本身、但尚未補牌的候選桌況。
 * @property actorPlayerId 執行動作的玩家 ID。
 * @property action 實際完成的遊戲動作。
 */
data class SupplementalDrawContext(
    val tableStateBeforeAction: TableState,
    val tableStateAfterAction: TableState,
    val actorPlayerId: Uuid,
    val action: GameAction,
)

/** 規則對動作後補牌的強型別決策。 */
sealed interface SupplementalDrawDecision {
    /** 此動作不需要補牌，候選桌況可直接成立。 */
    data object NotRequired : SupplementalDrawDecision

    /**
     * 補牌已成功解析。
     *
     * @property drawnTiles 依摸取順序排列的補牌；最後一張會放入玩家摸牌位置。
     * @property tileWall 更新後仍可供一般摸牌的牌堆。
     * @property reservedWallTiles 更新後的權威規則保留牌；實體擺法由另外的 layout policy 決定。
     * @property dynamicRuleState 更新後的規則動態狀態。
     * @property newlyRevealedTileIds 本次動作後新公開的牌張 ID。
     */
    data class Completed(
        val drawnTiles: List<IdentifiedTile>,
        val tileWall: TileWall,
        val reservedWallTiles: List<IdentifiedTile>,
        val dynamicRuleState: DynamicRuleState?,
        val newlyRevealedTileIds: Set<Uuid> = emptySet(),
    ) : SupplementalDrawDecision

    /**
     * 規則拒絕完成補牌。
     *
     * @property reasonId 完整 namespaced 拒絕原因 ID。
     */
    data class Rejected(val reasonId: String) : SupplementalDrawDecision {
        init {
            require(NAMESPACED_ID.matches(reasonId)) { "Supplemental draw rejection must use a namespaced id: $reasonId" }
        }
    }

    private companion object {
        /** 完整 namespaced ID 的格式。 */
        val NAMESPACED_ID = Regex("^[a-z0-9_.-]+:[a-z0-9/._-]+$")
    }
}

/** 未提供補牌能力的規則所使用的安全預設 policy。 */
object UnsupportedSupplementalDrawPolicy : SupplementalDrawPolicy {
    /** 所有送入此 policy 的動作都明確拒絕，不套用其他玩法的補牌規則。 */
    override fun resolve(context: SupplementalDrawContext): SupplementalDrawDecision = SupplementalDrawDecision.Rejected(UNSUPPORTED_REASON_ID)

    /** 規則未提供補牌能力的完整原因 ID。 */
    const val UNSUPPORTED_REASON_ID = "mahjongcraft:unsupported_supplemental_draw"
}

/** 跨規則共用的補牌決策原因 ID。 */
object SupplementalDrawReasonIds {
    /** 補牌所需的牌牆來源已耗盡。 */
    const val WALL_EXHAUSTED = "mahjongcraft:wall_exhausted"

    /** Policy 結果未通過 Flow 的牌張守恆或引用驗證。 */
    const val INVALID_RESULT = "mahjongcraft:invalid_supplemental_draw_result"
}
