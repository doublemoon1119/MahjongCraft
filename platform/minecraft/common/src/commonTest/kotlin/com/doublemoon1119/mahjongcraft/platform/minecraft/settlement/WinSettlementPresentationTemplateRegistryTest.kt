package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinSettlementPresentationTemplateRegistryTest {
    @Test
    fun `container appearance defaults to transparent borderless and padding free`() {
        val style = PresentationContainerStyle()
        assertEquals(0, style.backgroundArgb)
        assertEquals(0, style.borderArgb)
        assertEquals(0f, style.borderWidth)
        assertEquals(0f, style.padding)
        assertEquals(0f, style.dividerWidth)
    }

    @Test
    fun `built in generic template stays rule neutral`() {
        val registry = WinSettlementPresentationTemplateRegistryImpl()
        registry.registerBundledWinSettlementTemplates()
        val rendered = registry.findTemplate("mahjongcraft:generic").toString()
        assertFalse(rendered.contains(":dora", ignoreCase = true))
        assertFalse(rendered.contains(":han", ignoreCase = true))
        assertFalse(rendered.contains(":fu", ignoreCase = true))
    }

    @Test
    fun `built in riichi template uses the same public animation and identity primitives`() {
        val registry = WinSettlementPresentationTemplateRegistryImpl()
        registry.registerBundledWinSettlementTemplates()
        val rendered = registry.findTemplate("mahjongcraft:riichi").toString()

        assertTrue(rendered.contains("Animated"))
        assertTrue(rendered.contains("PlayerIdentity"))
        assertTrue(rendered.contains("Box"))
        assertTrue(rendered.contains("Positioned"))
        assertTrue(rendered.contains("TileGroups"))
    }

    @Test
    fun `riichi indicator providers fill unrevealed slots with tile backs`() {
        val registry = WinSettlementPresentationTemplateRegistryImpl()
        registry.registerBundledWinSettlementTemplates()
        val doraId = PresentationFieldId("mahjongcraft:riichi_dora")
        val snapshot = WinSettlementPresentationFieldSnapshot(
            outcomeId = "mahjongcraft:ron",
            isTsumo = false,
            winnerId = "winner",
            winnerDisplayName = "Winner",
            winnerIsAi = false,
            responsiblePlayerId = "loser",
            responsiblePlayerDisplayName = "Loser",
            responsiblePlayerIsAi = false,
            totalScore = 7_700,
            tileAssetKeys = emptyList(),
            tileAssetGroups = emptyList(),
            winningTileAssetKey = null,
            extensionFields = listOf(
                ExtensionPresentationField(doraId, PresentationValue.TileListValue(listOf("man_1", "man_2"))),
            ),
        )

        val value = assertIs<PresentationValue.TileListValue>(registry.findFieldProvider(doraId)?.provide(snapshot))
        assertEquals(listOf("man_1", "man_2", "back", "back", "back"), value.assetKeys)
    }

    /** 內建日麻模板明確提供兩種指示牌的本地化欄位標題。 */
    @Test
    fun `built in riichi template exposes localized indicator labels`() {
        val registry = WinSettlementPresentationTemplateRegistryImpl()
        registry.registerBundledWinSettlementTemplates()
        val template = registry.findTemplate("mahjongcraft:riichi") ?: error("Missing built-in riichi template")

        assertEquals(
            "mahjongcraft.settlement.dora_indicator",
            template.detailFieldLabelKeys[PresentationFieldId("mahjongcraft:riichi_dora")],
        )
        assertEquals(
            "mahjongcraft.settlement.ura_dora_indicator",
            template.detailFieldLabelKeys[PresentationFieldId("mahjongcraft:riichi_ura_dora")],
        )
    }

    @Test
    fun `layout exposes compose style arrangements and weighted children`() {
        assertEquals(
            setOf("START", "CENTER", "END", "SPACE_BETWEEN", "SPACE_AROUND", "SPACE_EVENLY"),
            PresentationArrangement.entries.map(Enum<*>::name).toSet(),
        )
        val weighted = PresentationLayout.Weighted(PresentationLayout.Spacer(), weight = 2f)
        assertEquals(2f, weighted.weight)
        assertFailsWith<IllegalArgumentException> {
            PresentationLayout.Weighted(PresentationLayout.Spacer(), weight = 0f)
        }
        assertEquals(
            PresentationAlignment.CENTER,
            PresentationLayout.RepeatEntries(PresentationFieldId("example:entries")).verticalAlignment,
        )
    }

    @Test
    fun `third party template can compose player identity and controlled reveal effects`() {
        val identity = PresentationFieldId("example:winner")
        val animated = PresentationLayout.Animated(
            child = PresentationLayout.PlayerIdentity(identity, scale = 1.25f),
            timeline = PresentationTimeline(PresentationTimelineAnchor.SCORE_REVEAL, durationTicks = 14),
            effects = listOf(
                PresentationAnimationEffect.Fade(),
                PresentationAnimationEffect.ScaleKeyframes(
                    listOf(
                        ScaleKeyframe(0f, 1.35f),
                        ScaleKeyframe(0.55f, 0.94f),
                        ScaleKeyframe(1f, 1f),
                    ),
                ),
                PresentationAnimationEffect.HighlightSweep(),
            ),
        )
        val registry = WinSettlementPresentationTemplateRegistryImpl()
        registry.registerFieldProvider(identity) {
            PresentationValue.PlayerIdentityValue("player", "Player", isAi = false)
        }
        val labelField = PresentationFieldId("example:detail")
        registry.registerTemplate(
            WinSettlementPresentationTemplate(
                "example:animated",
                animated,
                detailFieldLabelKeys = mapOf(labelField to "example.detail.label"),
            ),
        )

        assertIs<PresentationLayout.Animated>(registry.findTemplate("example:animated")?.root)
        assertEquals("example.detail.label", registry.findTemplate("example:animated")?.detailFieldLabelKeys?.get(labelField))
    }

    @Test
    fun `declarative sounds require namespaced identifiers and bounded values`() {
        val cue = PresentationSoundCue(
            soundId = "example:score_reveal",
            anchor = PresentationTimelineAnchor.SCORE_REVEAL,
            volume = 0.4f,
            pitch = 0.9f,
        )
        assertEquals("example:score_reveal", cue.soundId)
        assertFailsWith<IllegalArgumentException> {
            PresentationSoundCue("missing_namespace", PresentationTimelineAnchor.PANEL_START)
        }
    }

    /** 日麻規則使用日麻模板，沒有綁定的規則使用通用模板。 */
    @Test
    fun `templates are selected by rule module`() {
        val registry = WinSettlementPresentationTemplateRegistryImpl()
        registry.registerBundledWinSettlementTemplates()

        assertEquals(BuiltInWinSettlementTemplateKeys.RIICHI, registry.findTemplateForRule(BuiltInRuleModuleIds.RIICHI)?.key)
        assertEquals(BuiltInWinSettlementTemplateKeys.GENERIC, registry.findTemplateForRule("custom:rule")?.key)
    }

    /** 日麻詳情欄位登記了格式化器；同一欄位不得重複登記。 */
    @Test
    fun `riichi detail formatters are registered once per field`() {
        val registry = WinSettlementPresentationTemplateRegistryImpl()
        registry.registerBundledWinSettlementTemplates()

        listOf(RiichiWinSettlementIds.YAKU_FIELD, RiichiWinSettlementIds.HAN_FU_FIELD, RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD).forEach { id ->
            assertNotNull(registry.findDetailTextFormatter(id))
        }
        assertNull(registry.findDetailTextFormatter(RiichiWinSettlementIds.DORA_FIELD))
        assertFailsWith<IllegalArgumentException> {
            registry.registerDetailTextFormatter(RiichiWinSettlementIds.YAKU_FIELD, FallbackWinSettlementDetailTextFormatter)
        }
        assertFailsWith<IllegalArgumentException> { registry.bindRuleTemplate(BuiltInRuleModuleIds.RIICHI, "example:other") }
    }

    @Test
    fun `frozen registry rejects later registration`() {
        val registry = WinSettlementPresentationTemplateRegistryImpl()
        registry.registerBundledWinSettlementTemplates()
        registry.freeze()
        assertFailsWith<IllegalStateException> {
            registry.registerTemplate(
                WinSettlementPresentationTemplate("example:late", PresentationLayout.Spacer()),
            )
        }
    }
}
