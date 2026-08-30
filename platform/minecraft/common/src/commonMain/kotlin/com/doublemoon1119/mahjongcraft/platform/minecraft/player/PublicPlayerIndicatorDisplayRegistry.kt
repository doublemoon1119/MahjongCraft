package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/** 公開 indicator 或 option 在 Minecraft 面板使用的本地化名稱與 RGB 顏色。 */
data class PublicPlayerIndicatorDisplay(
    val translationKey: String,
    val colorRgb: Int = 0xFFE08A,
) {
    init {
        require(translationKey.isNotBlank()) { "Public indicator translation key must not be blank" }
        require(colorRgb in 0..0xFFFFFF) { "Public indicator color must be RGB" }
    }
}

/** 完整 namespaced indicator／option ID 的凍結式 Minecraft 顯示 registry。 */
interface PublicPlayerIndicatorDisplayRegistry {
    val isFrozen: Boolean
    fun register(id: String, display: PublicPlayerIndicatorDisplay)
    fun find(id: String): PublicPlayerIndicatorDisplay?
    fun freeze()
}

/** [PublicPlayerIndicatorDisplayRegistry] 的記憶體實作。 */
class PublicPlayerIndicatorDisplayRegistryImpl : PublicPlayerIndicatorDisplayRegistry {
    private val displays = mutableMapOf<String, PublicPlayerIndicatorDisplay>()
    override var isFrozen: Boolean = false
        private set

    override fun register(id: String, display: PublicPlayerIndicatorDisplay) {
        check(!isFrozen) { "Public player indicator display registry is frozen" }
        require(':' in id) { "Public player indicator display ID must be namespaced: $id" }
        require(displays.putIfAbsent(id, display) == null) { "Duplicate public player indicator display ID: $id" }
    }

    override fun find(id: String): PublicPlayerIndicatorDisplay? = displays[id]

    override fun freeze() {
        isFrozen = true
    }
}
