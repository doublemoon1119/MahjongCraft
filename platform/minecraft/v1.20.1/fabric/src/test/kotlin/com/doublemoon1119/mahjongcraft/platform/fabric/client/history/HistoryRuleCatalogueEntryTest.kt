package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueConfigSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證從歷史規則設定頁開啟規則一覽時的規則與設定。 */
class HistoryRuleCatalogueEntryTest {
    /** 保存設定解得出時，使用它的規則與設定，來源為歷史對局設定。 */
    @Test
    fun `decoded settings open with their rule and settings`() {
        val config = RiichiRuleConfig(redDoraCount = 0)

        val context = historyRuleCatalogueContext(decodedRuleModuleId = DECODED_RULE, decodedRuleConfig = config, summaryRuleModuleId = SUMMARY_RULE)

        assertEquals(DECODED_RULE, context?.ruleModuleId)
        assertEquals(RuleCatalogueConfigSource.HISTORY, context?.source)
        assertEquals(config, context?.config)
    }

    /** 保存設定解不出時，改用摘要記錄的規則，設定標為無法取得，不以一般說明替代。 */
    @Test
    fun `undecodable settings keep the summary rule without settings`() {
        val context = historyRuleCatalogueContext(decodedRuleModuleId = null, decodedRuleConfig = null, summaryRuleModuleId = SUMMARY_RULE)

        assertEquals(SUMMARY_RULE, context?.ruleModuleId)
        assertEquals(RuleCatalogueConfigSource.HISTORY, context?.source)
        assertNull(context?.config)
    }

    /** 解出的規則 ID 不合法時同樣改用摘要記錄的規則。 */
    @Test
    fun `invalid decoded rule falls back to the summary rule`() {
        val context = historyRuleCatalogueContext(decodedRuleModuleId = "not a rule", decodedRuleConfig = RiichiRuleConfig(), summaryRuleModuleId = SUMMARY_RULE)

        assertEquals(SUMMARY_RULE, context?.ruleModuleId)
        assertNull(context?.config)
    }

    /** 沒有任何合法規則時不提供規則一覽。 */
    @Test
    fun `no usable rule gives no context`() {
        assertNull(historyRuleCatalogueContext(decodedRuleModuleId = null, decodedRuleConfig = null, summaryRuleModuleId = null))
        assertNull(historyRuleCatalogueContext(decodedRuleModuleId = null, decodedRuleConfig = null, summaryRuleModuleId = "not a rule"))
    }

    /** 測試資料。 */
    private companion object {
        /** 由保存設定解出的規則。 */
        const val DECODED_RULE = "mahjongcraft:riichi"

        /** 摘要記錄的規則。 */
        const val SUMMARY_RULE = "example:other_rule"
    }
}
