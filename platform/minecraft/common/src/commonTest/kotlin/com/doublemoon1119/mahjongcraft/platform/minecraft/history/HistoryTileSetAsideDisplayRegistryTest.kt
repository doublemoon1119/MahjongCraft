package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiTranslationKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.BundledTaiwanMinecraftExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 驗證牌譜移出牌區標題與換牌用語的登記、查詢與凍結。 */
class HistoryTileSetAsideDisplayRegistryTest {
    /** 登記的牌區與規則可查回翻譯 key，未登記的查無結果。 */
    @Test
    fun `finds registered area names and replacement names only`() {
        val registry = HistoryTileSetAsideDisplayRegistryImpl().apply {
            register(HistoryTileAreaDisplayDefinition("example:north", "test.north"))
            register(HistoryTileReplacementDisplayDefinition("example:rule", "test.replacement"))
        }

        assertEquals("test.north", registry.findAreaName("example:north"))
        assertNull(registry.findAreaName("example:other"))
        assertEquals("test.replacement", registry.findReplacementName("example:rule"))
        assertNull(registry.findReplacementName("example:other"))
        assertNull(registry.findReplacementName(null))
    }

    /** 重複項目、非 namespaced ID 與凍結後登記都會失敗。 */
    @Test
    fun `rejects duplicates invalid ids and late registrations`() {
        val registry = HistoryTileSetAsideDisplayRegistryImpl().apply {
            register(HistoryTileAreaDisplayDefinition("example:north", "test.north"))
            register(HistoryTileReplacementDisplayDefinition("example:rule", "test.replacement"))
        }

        assertFailsWith<IllegalArgumentException> { registry.register(HistoryTileAreaDisplayDefinition("example:north", "test.other")) }
        assertFailsWith<IllegalArgumentException> { registry.register(HistoryTileReplacementDisplayDefinition("example:rule", "test.other")) }
        assertFailsWith<IllegalArgumentException> { registry.register(HistoryTileAreaDisplayDefinition("north", "test.other")) }
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.register(HistoryTileAreaDisplayDefinition("example:late", "test.late")) }
    }

    /** 三人日麻的北牌區有自己的標題；台灣麻將的花牌區與補花用語也由規則登記。 */
    @Test
    fun `bundled rules register their tile areas and replacement wording`() {
        val registry = HistoryTileSetAsideDisplayRegistryImpl().apply {
            BundledRiichiMinecraftExtension.registerHistoryTileSetAsideDisplays(this)
            BundledTaiwanMinecraftExtension.registerHistoryTileSetAsideDisplays(this)
        }

        assertEquals(RiichiTranslationKeys.HISTORY_TILE_AREA_NORTH, registry.findAreaName(RiichiPlayerTileAreaIds.NORTH))
        assertNotNull(registry.findAreaName(TaiwanPlayerTileAreaIds.FLOWERS))
        assertNotNull(registry.findReplacementName(BuiltInRuleModuleIds.TAIWAN))
        assertNull(registry.findReplacementName(BuiltInRuleModuleIds.RIICHI))
    }
}
