package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationCue
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult

/** 將指定規則的手牌價值結果解析成單一主要胡牌展示提示。 */
fun interface WinCelebrationCueResolver {
    /** 無需加碼展示時回傳 `null`。 */
    fun resolve(result: HandValueResult): WinCelebrationCue?
}

/** 規則模組 ID 與 [WinCelebrationCueResolver] 的註冊中心。 */
interface WinCelebrationCueResolverRegistry {
    /** 註冊指定規則模組的 resolver；同一 ID 不得重複。 */
    fun register(ruleModuleId: String, resolver: WinCelebrationCueResolver)

    /** 凍結後不得再註冊。 */
    fun freeze()

    /** 解析指定規則結果；未註冊或不適用時回傳 `null`。 */
    fun resolve(ruleModuleId: String, result: HandValueResult): WinCelebrationCue?
}

/** [WinCelebrationCueResolverRegistry] 的記憶體實作。 */
class WinCelebrationCueResolverRegistryImpl : WinCelebrationCueResolverRegistry {
    private val resolvers = mutableMapOf<String, WinCelebrationCueResolver>()
    private var frozen = false

    override fun register(ruleModuleId: String, resolver: WinCelebrationCueResolver) {
        check(!frozen) { "Win celebration cue resolver registry is frozen" }
        require(ruleModuleId.isNotBlank()) { "Rule module id must not be blank" }
        require(resolvers.putIfAbsent(ruleModuleId, resolver) == null) { "Duplicate win celebration cue resolver: $ruleModuleId" }
    }

    override fun freeze() {
        frozen = true
    }

    override fun resolve(ruleModuleId: String, result: HandValueResult): WinCelebrationCue? = resolvers[ruleModuleId]?.resolve(result)
}
