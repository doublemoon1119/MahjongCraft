package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 一次玩家動作完成、且已到達可判定後續規則結果的權威上下文。
 *
 * [action] 是實際完成的動作，不以規則專用 trigger 類型代替；各 resolver 應自行辨認是否關心該動作。
 * 呼叫端必須等候相關反應與必要的後續胡牌機會結束後才建立 context，避免提早終止合法流程。
 *
 * @property actorPlayerId 完成動作的玩家 ID。
 * @property action 實際完成的權威動作。
 * @property tableState 動作及其必要反應處理完成後的權威桌況。
 */
data class CompletedGameActionContext(
    val actorPlayerId: Uuid,
    val action: GameAction,
    val tableState: TableState,
)

/** 特定 [CompletedGameActionContext] 下，以純邏輯判定是否構成某種主動觸發的途中流局。 */
interface PostActionExhaustiveDrawResolver {
    /** Resolver 的完整 namespaced ID，同時作為穩定排序的最後決勝鍵。 */
    val id: String

    /** 此 resolver 所屬的規則模組 ID。 */
    val ruleModuleId: String

    /** 數值越小越先判定；同優先序再依 [id] 排序。 */
    val priority: Int

    /** 成立時回傳對應的流局原因，不認得 [context] 或不成立時回傳 `null`，讓下一個 resolver 繼續判定。 */
    fun resolve(context: CompletedGameActionContext, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason?
}

/** 可於 bootstrap 登記、完成後凍結的主動觸發途中流局 resolver registry。 */
class PostActionExhaustiveDrawResolverRegistry {
    /** 尚未凍結的 resolver 集合。 */
    private val resolvers = mutableListOf<PostActionExhaustiveDrawResolver>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 登記 resolver；ID 不得重複。 */
    fun register(resolver: PostActionExhaustiveDrawResolver) {
        check(!frozen) { "Post-action exhaustive draw resolver registry is frozen" }
        require(resolvers.none { it.id == resolver.id }) { "Post-action exhaustive draw resolver already registered: ${resolver.id}" }
        resolvers += resolver
    }

    /** 凍結並固定後續判定順序。 */
    fun freeze() {
        if (frozen) return
        resolvers.sortWith(compareBy(PostActionExhaustiveDrawResolver::priority, PostActionExhaustiveDrawResolver::id))
        frozen = true
    }

    /** 依穩定順序回傳第一個成立的流局原因。 */
    fun resolve(context: CompletedGameActionContext, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason? {
        check(frozen) { "Post-action exhaustive draw resolver registry must be frozen before use" }
        return resolvers.asSequence()
            .filter { it.ruleModuleId == ruleModule.id }
            .mapNotNull { it.resolve(context, ruleModule) }
            .firstOrNull()
    }
}

/** 將同一途中流局動作記錄至所有玩家的權威動作歷史。 */
internal fun TableState.recordExhaustiveDrawForAllPlayers(reason: ExhaustiveDrawReason): TableState = copy(
    players = players.map { player -> player.recordAction(GameAction.ExhaustiveDraw(reason)) },
)
