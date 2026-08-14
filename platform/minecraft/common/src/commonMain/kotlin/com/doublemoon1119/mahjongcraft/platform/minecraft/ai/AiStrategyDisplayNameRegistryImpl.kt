package com.doublemoon1119.mahjongcraft.platform.minecraft.ai

/**
 * [AiStrategyDisplayNameRegistry] 的預設記憶體實作。
 *
 * 建構時不預先加入任何映射；內建與第三方顯示名稱使用相同的 [register] 流程，並由外層組裝完成後
 * 呼叫 [freeze]。此類別不依賴 Koin，runtime single 由外層 DI module 管理。
 */
class AiStrategyDisplayNameRegistryImpl : AiStrategyDisplayNameRegistry {
    /** 依策略 key 保存顯示名稱 translation key。 */
    private val translationKeysByStrategyKey = mutableMapOf<String, String>()

    override var isFrozen: Boolean = false
        private set

    override fun register(strategyKey: String, translationKey: String) {
        check(!isFrozen) { "AI strategy display name registry is frozen" }
        require(strategyKey !in translationKeysByStrategyKey) {
            "Display name already registered for AI strategy: $strategyKey"
        }
        translationKeysByStrategyKey[strategyKey] = translationKey
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(strategyKey: String): String? = translationKeysByStrategyKey[strategyKey]
}
