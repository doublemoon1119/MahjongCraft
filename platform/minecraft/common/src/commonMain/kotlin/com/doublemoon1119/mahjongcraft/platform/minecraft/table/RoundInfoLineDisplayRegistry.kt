package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoundInfoLineDisplayRegistrar

/**
 * 將規則模組的局況 provider 與各行翻譯資訊集中註冊，供呈現端組出實際文字。
 * 未註冊 provider 的規則不會顯示局況面板，但不影響遊戲流程；擴充只透過 [RoundInfoLineDisplayRegistrar] 登記。
 */
interface RoundInfoLineDisplayRegistry : RoundInfoLineDisplayRegistrar {

    /** 查詢指定 key 的翻譯資訊；未登記時回傳 null。 */
    fun find(key: String): RoundInfoLineDisplay?

    /** 依 namespaced 訊息 ID 查詢翻譯定義；未登記時回傳 null。 */
    fun findMessage(id: String): RoundInfoMessageDefinition?

    /** 依規則模組建立完整局況行；未註冊時回傳空清單。 */
    fun buildLines(ruleModuleId: String, tableState: TableState): List<RoundInfoLine>

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
