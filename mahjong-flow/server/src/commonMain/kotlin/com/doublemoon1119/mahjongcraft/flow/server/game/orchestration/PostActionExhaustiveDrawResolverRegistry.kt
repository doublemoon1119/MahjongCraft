package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 特定玩家動作剛完成、確定無人可反應之後才會發生的主動觸發時機。
 *
 * 跟 [PostReactionRoundOutcomeResolver]（單一、被動觸發：只在確定普通荒牌流局之前檢查一次）不同，
 * 這裡的每個時機都是由某個特定玩家動作主動觸發，同一場對局可能發生多次，也可能完全不會發生；
 * 各 resolver 應只回應自己認得的時機，其餘時機一律回傳 null，避免跨時機誤判成立。
 */
sealed interface PostActionTrigger {
    /** 觸發當下的桌況快照，供 resolver 純邏輯判定使用。 */
    val tableState: TableState

    /** 一張捨牌確定沒有任何人可以吃/碰/槓/榮和之後。 */
    data class DiscardCompleted(override val tableState: TableState) : PostActionTrigger

    /** 一次立直宣告確定沒有任何人可以吃/碰/槓/榮和之後。 */
    data class RiichiDeclared(override val tableState: TableState) : PostActionTrigger

    /** 一次槓牌的嶺上摸牌已經處理完畢（含玩家已有機會嘗試嶺上開花自摸）之後。 */
    data class KanDeclared(override val tableState: TableState) : PostActionTrigger
}

/** 特定 [PostActionTrigger] 時機下，以純邏輯判定是否構成某種主動觸發的途中流局。 */
interface PostActionExhaustiveDrawResolver {
    /** Resolver 的完整 namespaced ID，同時作為穩定排序的最後決勝鍵。 */
    val id: String

    /** 此 resolver 所屬的規則模組 ID。 */
    val ruleModuleId: String

    /** 數值越小越先判定；同優先序再依 [id] 排序。 */
    val priority: Int

    /** 成立時回傳對應的流局原因，不認得 [trigger] 或不成立時回傳 `null`，讓下一個 resolver 繼續判定。 */
    fun resolve(trigger: PostActionTrigger, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason?
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
    fun resolve(trigger: PostActionTrigger, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason? {
        check(frozen) { "Post-action exhaustive draw resolver registry must be frozen before use" }
        return resolvers.asSequence()
            .filter { it.ruleModuleId == ruleModule.id }
            .mapNotNull { it.resolve(trigger, ruleModule) }
            .firstOrNull()
    }
}
