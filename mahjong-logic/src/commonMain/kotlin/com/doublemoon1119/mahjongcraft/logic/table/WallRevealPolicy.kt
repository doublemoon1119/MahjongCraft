package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import kotlin.uuid.Uuid

/** 規則可以處理牌牆公開牌狀態的流程節點。 */
enum class WallRevealCheckpoint {
    /** 規則要求的補牌已完成。 */
    AFTER_SUPPLEMENTAL_DRAW,

    /** 即將開始另一次規則補牌。 */
    BEFORE_SUPPLEMENTAL_DRAW,

    /** 捨牌反應已結束且無人胡牌。 */
    AFTER_DISCARD_REACTIONS,

    /** 胡牌已正式成立。 */
    WIN_CONFIRMED,
}

/**
 * 牌牆公開 policy 的完整輸入。
 *
 * @property checkpoint 目前抵達的規則中立流程節點。
 * @property tableState 目前的權威桌況。
 * @property actorPlayerId 觸發此節點的玩家；沒有單一玩家時為 null。
 * @property sourceAction 觸發此節點的已完成動作；沒有單一動作時為 null。
 */
data class WallRevealContext(
    val checkpoint: WallRevealCheckpoint,
    val tableState: TableState,
    val actorPlayerId: Uuid? = null,
    val sourceAction: GameAction? = null,
)

/** 規則對牌牆公開狀態的強型別決策。 */
sealed interface WallRevealDecision {
    /** 此節點不需要改變公開牌狀態。 */
    data object NoChange : WallRevealDecision

    /**
     * 公開牌狀態已成功更新。
     *
     * @property dynamicRuleState 更新後的規則動態狀態。
     * @property newlyRevealedTileIds 本次新成為公開資訊的牌張 ID。
     */
    data class Updated(
        val dynamicRuleState: DynamicRuleState,
        val newlyRevealedTileIds: Set<Uuid> = emptySet(),
    ) : WallRevealDecision

    /**
     * 規則拒絕更新公開牌狀態。
     *
     * @property reasonId 完整 namespaced 拒絕原因 ID。
     */
    data class Rejected(val reasonId: String) : WallRevealDecision {
        init {
            require(NAMESPACED_ID.matches(reasonId)) { "Wall reveal rejection must use a namespaced id: $reasonId" }
        }
    }

    private companion object {
        /** 完整 namespaced ID 的格式。 */
        val NAMESPACED_ID = Regex("^[a-z0-9_.-]+:[a-z0-9/._-]+$")
    }
}

/** 由規則決定指定流程節點是否公開或取消等待中的牌牆資訊。 */
fun interface WallRevealPolicy {
    /** 根據 [context] 建立不可變的牌牆公開決策。 */
    fun resolve(context: WallRevealContext): WallRevealDecision
}

/** 沒有額外牌牆公開時序需求的安全預設 policy。 */
object NoOpWallRevealPolicy : WallRevealPolicy {
    /** 所有流程節點皆維持原狀。 */
    override fun resolve(context: WallRevealContext): WallRevealDecision = WallRevealDecision.NoChange
}
