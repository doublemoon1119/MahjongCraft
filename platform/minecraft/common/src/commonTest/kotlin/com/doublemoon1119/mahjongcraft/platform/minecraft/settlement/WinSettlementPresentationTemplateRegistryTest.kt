package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

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
        registry.registerBuiltInWinSettlementTemplates()
        val rendered = registry.findTemplate("mahjongcraft:generic").toString()
        assertFalse(rendered.contains(":dora", ignoreCase = true))
        assertFalse(rendered.contains(":han", ignoreCase = true))
        assertFalse(rendered.contains(":fu", ignoreCase = true))
    }

    @Test
    fun `built in riichi template uses the same public animation and identity primitives`() {
        val registry = WinSettlementPresentationTemplateRegistryImpl()
        registry.registerBuiltInWinSettlementTemplates()
        val rendered = registry.findTemplate("mahjongcraft:riichi").toString()

        kotlin.test.assertTrue(rendered.contains("Animated"))
        kotlin.test.assertTrue(rendered.contains("PlayerIdentity"))
        kotlin.test.assertTrue(rendered.contains("Box"))
        kotlin.test.assertTrue(rendered.contains("Positioned"))
        kotlin.test.assertTrue(rendered.contains("TileGroups"))
    }

    @Test
    fun `riichi indicator providers fill unrevealed slots with tile backs`() {
        val registry = WinSettlementPresentationTemplateRegistryImpl()
        registry.registerBuiltInWinSettlementTemplates()
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
        registry.registerTemplate(WinSettlementPresentationTemplate("example:animated", animated))

        assertIs<PresentationLayout.Animated>(registry.findTemplate("example:animated")?.root)
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

    @Test
    fun `frozen registry rejects later registration`() {
        val registry = WinSettlementPresentationTemplateRegistryImpl()
        registry.registerBuiltInWinSettlementTemplates()
        registry.freeze()
        assertFailsWith<IllegalStateException> {
            registry.registerTemplate(
                WinSettlementPresentationTemplate("example:late", PresentationLayout.Spacer()),
            )
        }
    }
}
