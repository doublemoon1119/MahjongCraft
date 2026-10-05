package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationWinner
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult

/** 將指定規則的手牌價值結果解析成值得額外展示的理由 ID；順序與用途見 [WinCelebrationWinner.cueIds]。 */
fun interface WinCelebrationCueResolver {
    /** 不需要額外展示時回傳空清單。 */
    fun resolve(result: HandValueResult): List<String>
}

/** 規則模組 ID 與 [WinCelebrationCueResolver] 的註冊中心。 */
interface WinCelebrationCueResolverRegistry {
    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String>

    /** 註冊指定規則模組的 resolver；同一 ID 不得重複。 */
    fun register(ruleModuleId: String, resolver: WinCelebrationCueResolver)

    /** 凍結後不得再註冊。 */
    fun freeze()

    /** 解析指定規則結果；未註冊或不適用時回傳空清單。 */
    fun resolve(ruleModuleId: String, result: HandValueResult): List<String>
}

/** [WinCelebrationCueResolverRegistry] 的記憶體實作。 */
class WinCelebrationCueResolverRegistryImpl : WinCelebrationCueResolverRegistry {
    private val resolvers = mutableMapOf<String, WinCelebrationCueResolver>()
    private var frozen = false

    override val registrationKeys: Set<String> get() = resolvers.keys.toSet()

    override fun register(ruleModuleId: String, resolver: WinCelebrationCueResolver) {
        check(!frozen) { "Win celebration cue resolver registry is frozen" }
        require(ruleModuleId.isNotBlank()) { "Rule module id must not be blank" }
        require(resolvers.putIfAbsent(ruleModuleId, resolver) == null) { "Duplicate win celebration cue resolver: $ruleModuleId" }
    }

    override fun freeze() {
        frozen = true
    }

    override fun resolve(ruleModuleId: String, result: HandValueResult): List<String> = resolvers[ruleModuleId]?.resolve(result).orEmpty()
}
