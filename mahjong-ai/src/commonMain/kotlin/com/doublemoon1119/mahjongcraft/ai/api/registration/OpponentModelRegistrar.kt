package com.doublemoon1119.mahjongcraft.ai.api.registration

import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelDefinition

/** 擴充登記對手模型時取得的登記介面；建立模型與凍結由 MahjongCraft 進行。 */
interface OpponentModelRegistrar {
    /** 目前已登記的規則模組 ID，依登記順序排列。 */
    val registrationKeys: Set<String>

    /**
     * 登記 definition 指定的對手模型；每次建立模型時，以該局的規則模組與決策者的讀牌深度呼叫其 factory 一次。
     *
     * 凍結後或重複登記同一個 definition 的規則時拋出例外。
     *
     * @param definition 規則模組識別碼與對手模型延遲工廠。
     */
    fun register(definition: OpponentModelDefinition)
}
