package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.defaultSubmission
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 完成一個開局準備步驟後的規則結果。 */
data class RoundPreparationResolution(
    val tableState: TableState,
    val nextStep: PendingRoundPreparation?,
)

/** 一種規則模組的開局準備流程解析器。 */
interface RoundPreparationResolver {
    /** 此解析器所屬的規則模組 ID。 */
    val ruleModuleId: String

    /** 發牌後建立第一個步驟；不需要準備時回傳 null。 */
    fun begin(tableState: TableState, ruleModule: MahjongRuleModule<*>): PendingRoundPreparation?

    /** 驗證通過核心結構檢查的玩家提交。 */
    fun accepts(
        tableState: TableState,
        preparation: PendingRoundPreparation,
        playerId: Uuid,
        submission: RoundPreparationSubmission,
        ruleModule: MahjongRuleModule<*>,
    ): Boolean = true

    /** 為真人逾時或無法完成決策的 AI 產生可重現提交。 */
    fun fallbackSubmission(
        tableState: TableState,
        preparation: PendingRoundPreparation,
        playerId: Uuid,
        ruleModule: MahjongRuleModule<*>,
    ): RoundPreparationSubmission = preparation.inputSpecsByPlayerId.getValue(playerId).defaultSubmission()

    /** 收齊提交或遇到無參與者的自動步驟後，套用此步驟並解析下一步。 */
    fun resolve(
        tableState: TableState,
        preparation: PendingRoundPreparation,
        ruleModule: MahjongRuleModule<*>,
    ): RoundPreparationResolution
}

/** 開局準備解析器的可凍結註冊表。 */
class RoundPreparationResolverRegistry {
    /** 依規則模組 ID 索引的解析器。 */
    private val resolvers = mutableMapOf<String, RoundPreparationResolver>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 登記一個規則模組的解析器。 */
    fun register(resolver: RoundPreparationResolver) {
        check(!frozen) { "Round preparation resolver registry is frozen" }
        require(':' in resolver.ruleModuleId && resolver.ruleModuleId.substringAfter(':').isNotBlank()) {
            "Round preparation rule module ID must be namespaced: ${resolver.ruleModuleId}"
        }
        require(resolver.ruleModuleId !in resolvers) {
            "Round preparation resolver already registered for ${resolver.ruleModuleId}"
        }
        resolvers[resolver.ruleModuleId] = resolver
    }

    /** 取得指定規則模組的解析器。 */
    fun find(ruleModuleId: String): RoundPreparationResolver? = resolvers[ruleModuleId]

    /** 凍結此註冊表。 */
    fun freeze() {
        frozen = true
    }
}
