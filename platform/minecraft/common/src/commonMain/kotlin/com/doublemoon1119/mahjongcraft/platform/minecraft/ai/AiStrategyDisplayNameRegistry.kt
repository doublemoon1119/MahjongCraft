package com.doublemoon1119.mahjongcraft.platform.minecraft.ai

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry

/**
 * 登記 AI 策略識別碼對應的名稱翻譯鍵，供聊天、指令與提示文字使用。
 *
 * 與 [MinecraftTileAssetRegistry] 一樣，內建與第三方資料共用註冊流程。
 * 此處只管理平台顯示名稱；策略本身由 [MahjongAiStrategyRegistry] 管理。
 */
interface AiStrategyDisplayNameRegistry {
    /** 目前已登記策略 key 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: AiStrategyDisplayNameDefinition)

    /** 凍結 registry；後續呼叫 [register] 將失敗。 */
    fun freeze()

    /**
     * 依 [strategyKey] 尋找對應的顯示名稱 translation key；尚未註冊時回傳 null。
     *
     * @param strategyKey AI 策略的穩定 key。
     * @return 已登記的 translation key，或尚未登記時的 null。
     */
    fun find(strategyKey: String): String?
}
