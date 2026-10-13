package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 將規則模組的局況 provider 與各行翻譯資訊集中註冊，供呈現端組出實際文字。
 * 未註冊 provider 的規則不會顯示局況面板，但不影響遊戲流程。
 */
interface RoundInfoLineDisplayRegistry {
    /** 目前已登記局況資訊 key 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記一個局況顯示行 key 的翻譯資訊。
     *
     * @param definition 顯示行的完整登記描述。
     */
    fun register(definition: RoundInfoLineDisplayDefinition)

    /**
     * 登記指定規則模組的局況資料 provider。
     *
     * @param definition provider 的完整登記描述。
     */
    fun register(definition: RoundInfoLineProviderDefinition)

    /** 查詢指定 key 的翻譯資訊；未登記時回傳 null。 */
    fun find(key: String): RoundInfoLineDisplay?

    /** 依規則模組建立完整局況行；未註冊時回傳空清單。 */
    fun buildLines(ruleModuleId: String, tableState: TableState): List<RoundInfoLine>

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
