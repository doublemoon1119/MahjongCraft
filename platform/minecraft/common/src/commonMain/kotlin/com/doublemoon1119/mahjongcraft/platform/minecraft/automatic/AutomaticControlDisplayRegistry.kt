package com.doublemoon1119.mahjongcraft.platform.minecraft.automatic

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/** 一項自動操作在 Minecraft 中使用的名稱、說明及排列順序。 */
data class AutomaticControlDisplay(
    val labelTranslationKey: String,
    val descriptionTranslationKey: String,
    val displayOrder: Int,
) {
    init {
        require(labelTranslationKey.isNotBlank()) { "Automatic control label translation key must not be blank" }
        require(descriptionTranslationKey.isNotBlank()) { "Automatic control description translation key must not be blank" }
    }
}

/** 管理自動操作 Minecraft 顯示資料的凍結式 registry。 */
interface AutomaticControlDisplayRegistry {
    /** 目前已登記 control ID 的穩定快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記一項自動操作的顯示資料。 */
    fun register(controlId: String, display: AutomaticControlDisplay)

    /** 查詢指定 control ID 的顯示資料；未登記時回傳 `null`。 */
    fun find(controlId: String): AutomaticControlDisplay?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}

/** [AutomaticControlDisplayRegistry] 的記憶體實作。 */
class AutomaticControlDisplayRegistryImpl : AutomaticControlDisplayRegistry {
    private val displays = mutableMapOf<String, AutomaticControlDisplay>()

    override val registrationKeys: Set<String>
        get() = displays.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(controlId: String, display: AutomaticControlDisplay) {
        check(!isFrozen) { "Automatic control display registry is frozen" }
        MinecraftResourceIds.requireValid(controlId) { "Automatic control ID must be namespaced: $controlId" }
        require(displays.putIfAbsent(controlId, display) == null) {
            "Automatic control display already registered: $controlId"
        }
    }

    override fun find(controlId: String): AutomaticControlDisplay? = displays[controlId]

    override fun freeze() {
        isFrozen = true
    }
}

/** Minecraft client 長期保存之自動操作偏好的穩定 ID。 */
object BuiltInMinecraftAutomaticControlIds {
    /** 自動維持手牌排序。 */
    const val AUTO_SORT_HAND: String = "mahjongcraft:auto_sort_hand"
}
