package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 規則專屬局況顯示行對應的翻譯資訊；局況行本身由同一 registry 中的規則呈現 provider 建立。
 *
 * @property translationKey 這一行文字使用的翻譯 key。
 */
data class RoundInfoLineDisplay(
    val translationKey: String,
)

/** 依目前規則狀態建立桌面局況顯示行。 */
fun interface RoundInfoLineProvider {
    fun buildLines(tableState: TableState): List<RoundInfoLine>
}

/**
 * 將規則模組的局況 provider 與各行翻譯資訊集中註冊，供呈現端組出實際文字。
 * 未註冊 provider 的規則不會顯示局況面板，但不影響遊戲流程。
 */
interface RoundInfoLineDisplayRegistry {
    /** 目前已登記局況資訊 key 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記一個局況顯示行 key 的翻譯資訊。 */
    fun register(key: String, display: RoundInfoLineDisplay)

    /** 登記指定規則模組的局況資料 provider。 */
    fun register(ruleModuleId: String, provider: RoundInfoLineProvider)

    /** 查詢指定 key 的翻譯資訊；未登記時回傳 null。 */
    fun find(key: String): RoundInfoLineDisplay?

    /** 依規則模組建立完整局況行；未註冊時回傳空清單。 */
    fun buildLines(ruleModuleId: String, tableState: TableState): List<RoundInfoLine>

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}

/** [RoundInfoLineDisplayRegistry] 的記憶體實作。 */
class RoundInfoLineDisplayRegistryImpl : RoundInfoLineDisplayRegistry {
    /** 依局況顯示行 key 索引的翻譯資訊。 */
    private val displays = mutableMapOf<String, RoundInfoLineDisplay>()

    private val providers = mutableMapOf<String, RoundInfoLineProvider>()

    override val registrationKeys: Set<String> get() = displays.keys + providers.keys

    override var isFrozen: Boolean = false
        private set

    override fun register(key: String, display: RoundInfoLineDisplay) {
        check(!isFrozen) { "Round info line display registry is frozen" }
        require(key.isNotBlank()) { "Round info line key must not be blank" }
        require(displays.putIfAbsent(key, display) == null) { "Duplicate round info line display: $key" }
    }

    override fun register(ruleModuleId: String, provider: RoundInfoLineProvider) {
        check(!isFrozen) { "Round info line display registry is frozen" }
        require(ruleModuleId.isNotBlank()) { "Round info provider rule module ID must not be blank" }
        require(providers.putIfAbsent(ruleModuleId, provider) == null) {
            "Duplicate round info provider: $ruleModuleId"
        }
    }

    override fun find(key: String): RoundInfoLineDisplay? = displays[key]

    override fun buildLines(ruleModuleId: String, tableState: TableState): List<RoundInfoLine> = providers[ruleModuleId]?.buildLines(tableState).orEmpty()

    override fun freeze() {
        isFrozen = true
    }
}
