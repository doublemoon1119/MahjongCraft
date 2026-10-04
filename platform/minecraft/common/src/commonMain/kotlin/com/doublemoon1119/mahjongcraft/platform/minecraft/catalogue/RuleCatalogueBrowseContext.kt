package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/** 目錄設定的來源；一般說明不代表任何房間或歷史對局的設定。 */
enum class RuleCatalogueConfigSource {
    /** 規則來源提供的預設設定。 */
    GENERAL,

    /** 房間目前的設定。 */
    ROOM,

    /** 尚未套用的房間編輯草稿。 */
    ROOM_DRAFT,

    /** 歷史對局保存的設定。 */
    HISTORY,
}

/**
 * 開啟目錄時的唯讀脈絡，不因切換規則而改寫原設定。
 *
 * @property ruleModuleId 原規則 ID；一般說明可為 null，表示按穩定順序優先選擇已登記目錄的規則。
 * @property source 原設定來源。
 * @property config 已解析的原規則設定；非一般說明的 null 表示設定無法取得，不可默認替換為預設設定。
 */
data class RuleCatalogueBrowseContext(
    val ruleModuleId: String? = null,
    val source: RuleCatalogueConfigSource = RuleCatalogueConfigSource.GENERAL,
    val config: MahjongRuleConfig? = null,
) {
    init {
        ruleModuleId?.let { NamespacedId.requireValid(it) { "Invalid catalogue opening rule ID: $it" } }
        require(source == RuleCatalogueConfigSource.GENERAL || ruleModuleId != null) { "Actual catalogue context must identify its rule" }
        require(source != RuleCatalogueConfigSource.GENERAL || config == null) { "General catalogue context must not carry actual settings" }
    }
}
