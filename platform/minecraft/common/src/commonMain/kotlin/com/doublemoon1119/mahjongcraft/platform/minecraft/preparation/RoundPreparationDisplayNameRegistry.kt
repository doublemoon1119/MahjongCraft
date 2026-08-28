package com.doublemoon1119.mahjongcraft.platform.minecraft.preparation

/** 將開局準備 step／option 的完整 ID 映射到 Minecraft translation key。 */
interface RoundPreparationDisplayNameRegistry {
    /** Registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記一個 step 或 option 的本地化名稱。 */
    fun register(id: String, translationKey: String)

    /** 查詢本地化名稱；未知 ID 回傳 null，呼叫端應顯示完整 ID。 */
    fun find(id: String): String?

    /** 凍結 registry，禁止 runtime 再改變顯示名稱集合。 */
    fun freeze()
}

/** [RoundPreparationDisplayNameRegistry] 的記憶體實作。 */
class RoundPreparationDisplayNameRegistryImpl : RoundPreparationDisplayNameRegistry {
    /** 依完整 ID 索引的 translation key。 */
    private val translations = mutableMapOf<String, String>()

    override var isFrozen: Boolean = false
        private set

    override fun register(id: String, translationKey: String) {
        check(!isFrozen) { "Round-preparation display-name registry is frozen" }
        require(':' in id && id.substringAfter(':').isNotBlank()) {
            "Round-preparation display ID must be namespaced: $id"
        }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(translations.putIfAbsent(id, translationKey) == null) {
            "Duplicate round-preparation display ID: $id"
        }
    }

    override fun find(id: String): String? = translations[id]

    override fun freeze() {
        isFrozen = true
    }
}
