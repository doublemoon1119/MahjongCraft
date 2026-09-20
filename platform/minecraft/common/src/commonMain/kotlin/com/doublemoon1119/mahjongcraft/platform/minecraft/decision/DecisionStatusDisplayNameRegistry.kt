package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/** 所有規則共用的決策狀態識別碼。 */
object BuiltInDecisionStatusIds {
    /** 和牌資格沒有任何特殊限制，對應 `DiscardReadinessAnalyzer` 的中立預設。 */
    const val WIN_AVAILABLE: String = "mahjongcraft:win_available"
}

/**
 * 管理捨牌分析狀態（例如振聽、和牌資格）顯示名稱的凍結式 registry。
 *
 * 說法屬於規則，查詢以「規則模組 ID + 狀態 ID」為準，該規則沒有登記時退回中立預設；兩者都沒有時由呼叫端
 * 顯示原始 ID。
 */
interface DecisionStatusDisplayNameRegistry {
    /** 目前已登記組合的穩定 key 快照，格式為 `規則模組 ID/狀態 ID`，中立預設以 [DEFAULT_KEY] 表示。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記所有規則共用的中立預設。 */
    fun registerDefault(statusId: String, translationKey: String)

    /** 登記指定規則模組專屬的顯示名稱，覆寫同一個狀態的中立預設。 */
    fun register(ruleModuleId: String, statusId: String, translationKey: String)

    /** 查詢顯示名稱：先查 [ruleModuleId] 的登記，再查中立預設；都沒有時回傳 null。 */
    fun find(ruleModuleId: String?, statusId: String): String?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()

    companion object {
        /** [registrationKeys] 中代表中立預設的規則位置。 */
        const val DEFAULT_KEY: String = "default"
    }
}

/** [DecisionStatusDisplayNameRegistry] 的記憶體實作。 */
class DecisionStatusDisplayNameRegistryImpl : DecisionStatusDisplayNameRegistry {
    /** 依狀態 ID 索引的中立預設。 */
    private val defaults = mutableMapOf<String, String>()

    /** 依規則模組與狀態 ID 索引的規則專屬名稱。 */
    private val byRuleModule = mutableMapOf<Pair<String, String>, String>()

    override val registrationKeys: Set<String>
        get() = defaults.keys.mapTo(mutableSetOf()) { statusId -> "${DecisionStatusDisplayNameRegistry.DEFAULT_KEY}/$statusId" } +
            byRuleModule.keys.map { (ruleModuleId, statusId) -> "$ruleModuleId/$statusId" }

    override var isFrozen: Boolean = false
        private set

    override fun registerDefault(statusId: String, translationKey: String) {
        check(!isFrozen) { "Decision status display-name registry is frozen" }
        MinecraftResourceIds.requireValid(statusId) { "Decision status ID must be namespaced: $statusId" }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(defaults.putIfAbsent(statusId, translationKey) == null) {
            "Default decision status display name already registered: $statusId"
        }
    }

    override fun register(ruleModuleId: String, statusId: String, translationKey: String) {
        check(!isFrozen) { "Decision status display-name registry is frozen" }
        MinecraftResourceIds.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        MinecraftResourceIds.requireValid(statusId) { "Decision status ID must be namespaced: $statusId" }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(byRuleModule.putIfAbsent(ruleModuleId to statusId, translationKey) == null) {
            "Decision status display name already registered: $ruleModuleId/$statusId"
        }
    }

    override fun find(ruleModuleId: String?, statusId: String): String? = ruleModuleId?.let { byRuleModule[it to statusId] } ?: defaults[statusId]

    override fun freeze() {
        isFrozen = true
    }
}

/** 捨牌分析狀態顯示名稱的 translation key 前綴。 */
internal const val HUD_STATUS_PREFIX: String = MinecraftModMetadata.MOD_ID + ".hud."

/** 登記中立預設：和牌資格沒有特殊限制。 */
fun DecisionStatusDisplayNameRegistry.registerBuiltInDecisionStatusDisplayNames() {
    registerDefault(BuiltInDecisionStatusIds.WIN_AVAILABLE, HUD_STATUS_PREFIX + "win_availability.available")
}
