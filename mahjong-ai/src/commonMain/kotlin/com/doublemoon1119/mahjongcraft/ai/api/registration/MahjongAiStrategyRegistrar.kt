package com.doublemoon1119.mahjongcraft.ai.api.registration

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyDefinition

/** 擴充登記 AI 策略時取得的登記介面；解析與凍結由 MahjongCraft 進行。 */
interface MahjongAiStrategyRegistrar {
    /**
     * 登記策略描述；每次解析時呼叫其中的建立函式，讓各策略實例獨立保存自己的狀態。
     *
     * 凍結後或識別碼重複時拒絕登記，不覆寫先前擴充提供的策略。
     *
     * @param definition 策略 key 與每次解析時建立策略的延遲工廠。
     * @throws IllegalStateException 登記中心已凍結。
     * @throws IllegalArgumentException [definition] 的 key 已被使用或不是 namespaced ID。
     */
    fun register(definition: MahjongAiStrategyDefinition)
}
