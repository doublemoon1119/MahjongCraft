package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

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

    /**
     * 登記所有規則共用的中立預設。
     *
     * @param statusId 狀態的穩定 ID。
     * @param translationKey 顯示狀態名稱的 translation key。
     */
    fun registerDefault(statusId: String, translationKey: String)

    /**
     * 登記指定規則模組專屬的顯示名稱，優先於同一狀態的中立預設。
     *
     * @param ruleModuleId 規則模組 ID。
     * @param statusId 狀態的穩定 ID。
     * @param translationKey 顯示狀態名稱的 translation key。
     */
    fun register(ruleModuleId: String, statusId: String, translationKey: String)

    /**
     * 查詢顯示名稱：先查 [ruleModuleId] 的登記，再查中立預設；都沒有時回傳 null。
     *
     * @param ruleModuleId 規則模組 ID；null 時只查中立預設。
     * @param statusId 狀態的穩定 ID。
     * @return 找到的 translation key；都未登記時為 null。
     */
    fun find(ruleModuleId: String?, statusId: String): String?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()

    companion object {
        /** [registrationKeys] 中代表中立預設的規則位置。 */
        const val DEFAULT_KEY: String = "default"
    }
}

/** 捨牌分析狀態顯示名稱的 translation key 前綴。 */
internal const val HUD_STATUS_PREFIX: String = MinecraftModMetadata.MOD_ID + ".hud."
