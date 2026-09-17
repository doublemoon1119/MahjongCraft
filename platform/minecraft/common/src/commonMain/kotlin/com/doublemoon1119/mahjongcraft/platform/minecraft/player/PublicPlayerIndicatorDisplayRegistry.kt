package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 玩家相關公開標記在 Minecraft 面板使用的本地化名稱與 RGB 顏色。
 *
 * 公開標記包括座位上的公開 indicator 與其 option，以及胡牌結算排行中的付款原因；兩者都是規則提供、
 * 所有玩家與旁觀者都能看到的完整 namespaced ID。
 */
data class PublicPlayerIndicatorDisplay(
    val translationKey: String,
    val colorRgb: Int = 0xFFE08A,
) {
    init {
        require(translationKey.isNotBlank()) { "Public indicator translation key must not be blank" }
        require(colorRgb in 0..0xFFFFFF) { "Public indicator color must be RGB" }
    }
}

/** 玩家相關公開標記（indicator、option、付款原因）完整 namespaced ID 的凍結式 Minecraft 顯示 registry。 */
interface PublicPlayerIndicatorDisplayRegistry {
    /** 目前已登記 indicator ID 的快照。 */
    val registrationKeys: Set<String>

    val isFrozen: Boolean
    fun register(id: String, display: PublicPlayerIndicatorDisplay)
    fun find(id: String): PublicPlayerIndicatorDisplay?
    fun freeze()
}

/** [PublicPlayerIndicatorDisplayRegistry] 的記憶體實作。 */
class PublicPlayerIndicatorDisplayRegistryImpl : PublicPlayerIndicatorDisplayRegistry {
    private val displays = mutableMapOf<String, PublicPlayerIndicatorDisplay>()

    override val registrationKeys: Set<String> get() = displays.keys.toSet()
    override var isFrozen: Boolean = false
        private set

    override fun register(id: String, display: PublicPlayerIndicatorDisplay) {
        check(!isFrozen) { "Public player indicator display registry is frozen" }
        NamespacedId.requireValid(id) { "Public player indicator display ID must be namespaced: $id" }
        require(displays.putIfAbsent(id, display) == null) { "Duplicate public player indicator display ID: $id" }
    }

    override fun find(id: String): PublicPlayerIndicatorDisplay? = displays[id]

    override fun freeze() {
        isFrozen = true
    }
}
