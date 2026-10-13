package com.doublemoon1119.mahjongcraft.platform.minecraft.rule

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry

/**
 * 開放註冊的麻將規則模組顯示名稱對照表。
 *
 * 比照 [AiStrategyDisplayNameRegistry] 的既有設計。
 * key 用 [MahjongModuleRegistry] 本來就有的穩定
 * `String` 識別碼（`MahjongRuleModule.id`，例如 `"mahjongcraft:riichi"`），
 * 第三方規則模組登記時沿用同一個 id，不需要另外設計一套識別碼。
 */
interface RuleModuleDisplayNameRegistry {
    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String>

    /** 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: RuleModuleDisplayNameDefinition)

    /** 凍結 registry；凍結後不得新增顯示名稱。 */
    fun freeze()

    /** 查詢 [ruleModuleId] 的顯示名稱 translation key；未登記時回傳 null。 */
    fun find(ruleModuleId: String): String?
}
