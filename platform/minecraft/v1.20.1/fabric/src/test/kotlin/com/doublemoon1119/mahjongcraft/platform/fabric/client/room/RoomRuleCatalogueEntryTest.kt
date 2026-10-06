package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueConfigSource
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證從房間設定頁開啟規則一覽時的規則與設定來源。 */
class RoomRuleCatalogueEntryTest {
    /** 設定頁顯示的內容與房間目前設定相同時，來源為房間設定。 */
    @Test
    fun `unchanged settings open as room settings`() {
        val config = GameConfig(RiichiRuleConfig())

        val context = roomRuleCatalogueContext(ruleModuleId = RULE_ID, displayed = config, authoritative = config.copy())

        assertEquals(RULE_ID, context.ruleModuleId)
        assertEquals(RuleCatalogueConfigSource.ROOM, context.source)
        assertEquals(config.ruleConfig, context.config)
    }

    /** 有尚未套用的修改時，來源為房間設定草稿，並使用草稿中的規則設定。 */
    @Test
    fun `unapplied changes open as the room draft`() {
        val authoritative = GameConfig(RiichiRuleConfig())
        val draft = GameConfig(RiichiRuleConfig(redDoraCount = 0, allowOpenTanyao = false))

        val context = roomRuleCatalogueContext(ruleModuleId = RULE_ID, displayed = draft, authoritative = authoritative)

        assertEquals(RuleCatalogueConfigSource.ROOM_DRAFT, context.source)
        assertEquals(draft.ruleConfig, context.config)
    }

    /** 只改了流程設定（例如準備時間）也算尚未套用的草稿，但規則設定仍取自草稿。 */
    @Test
    fun `flow only changes still count as a draft`() {
        val authoritative = GameConfig(RiichiRuleConfig())
        val draft = authoritative.copy(flowConfig = GameFlowConfig(preparationBaseSeconds = 60))

        val context = roomRuleCatalogueContext(ruleModuleId = RULE_ID, displayed = draft, authoritative = authoritative)

        assertEquals(RuleCatalogueConfigSource.ROOM_DRAFT, context.source)
        assertEquals(authoritative.ruleConfig, context.config)
    }

    /** 測試資料。 */
    private companion object {
        /** 測試用規則 ID。 */
        const val RULE_ID = "mahjongcraft:riichi"
    }
}
