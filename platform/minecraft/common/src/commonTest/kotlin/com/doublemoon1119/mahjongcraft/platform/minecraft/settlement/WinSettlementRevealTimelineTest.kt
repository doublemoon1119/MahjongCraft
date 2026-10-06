package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiWinSettlementTemplates
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證逐條揭示之後的時間完全由版面決定，且內建模板與改寫前的節奏相同。 */
class WinSettlementRevealTimelineTest {
    private val summaryId = PresentationFieldId("mahjongcraft:test_summary")
    private val hanFuId = PresentationFieldId(RiichiWinSettlementIds.HAN_FU_FIELD)
    private val yakumanTotalId = PresentationFieldId(RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD)

    private fun animated(
        child: PresentationLayout,
        anchor: PresentationTimelineAnchor,
        offsetTicks: Int = 0,
        durationTicks: Int = 6,
    ) = PresentationLayout.Animated(
        child = child,
        timeline = PresentationTimeline(anchor = anchor, offsetTicks = offsetTicks, durationTicks = durationTicks),
        effects = listOf(PresentationAnimationEffect.Fade()),
    )

    private fun builtInLayout(key: String): PresentationLayout {
        val registry = WinSettlementPresentationTemplateRegistryImpl().apply { registerBundledWinSettlementTemplates() }
        return requireNotNull(registry.findTemplate(key)).root
    }

    /** 分數錨在逐條揭示之後時，取它的延遲。 */
    @Test
    fun `the score delay is read from the score node`() {
        val layout = PresentationLayout.Column(
            children = listOf(
                animated(PresentationLayout.Text(summaryId), PresentationTimelineAnchor.AFTER_ENTRIES),
                animated(
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE),
                    PresentationTimelineAnchor.AFTER_ENTRIES,
                    offsetTicks = 12,
                    durationTicks = 18,
                ),
            ),
        )

        assertEquals(12, WinSettlementRevealTimeline.scoreRevealDelayTicks(layout))
    }

    /** 分數沒有錨在逐條揭示之後時，逐條揭示一結束就出現。 */
    @Test
    fun `a score not placed after the entries has no delay`() {
        val layout = PresentationLayout.Column(
            children = listOf(
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE),
                animated(PresentationLayout.Text(summaryId), PresentationTimelineAnchor.AFTER_ENTRIES, offsetTicks = 4),
            ),
        )

        assertEquals(0, WinSettlementRevealTimeline.scoreRevealDelayTicks(layout))
    }

    /** 分數以外、排在逐條揭示之後的每一行都列出，由早到晚；這位贏家沒有的那一行不列入。 */
    @Test
    fun `lines after the entries are listed only when present`() {
        val otherId = PresentationFieldId("mahjongcraft:test_other")
        val layout = PresentationLayout.Column(
            children = listOf(
                PresentationLayout.IfPresent(
                    summaryId,
                    animated(PresentationLayout.Text(summaryId), PresentationTimelineAnchor.AFTER_ENTRIES, offsetTicks = 4),
                ),
                PresentationLayout.IfPresent(
                    otherId,
                    animated(PresentationLayout.Text(otherId), PresentationTimelineAnchor.AFTER_ENTRIES, offsetTicks = 1),
                ),
                animated(
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE),
                    PresentationTimelineAnchor.AFTER_ENTRIES,
                    offsetTicks = 8,
                ),
            ),
        )

        assertEquals(listOf(1, 4), WinSettlementRevealTimeline.lineRevealDelaysTicks(layout, setOf(summaryId, otherId)))
        assertEquals(listOf(4), WinSettlementRevealTimeline.lineRevealDelaysTicks(layout, setOf(summaryId)))
        assertEquals(emptyList(), WinSettlementRevealTimeline.lineRevealDelaysTicks(layout, emptySet()))
    }

    /**
     * 日麻模板：分數在逐條揭示結束後 8 tick 出現，翻符或役滿那一行在逐條揭示一結束時出現。
     *
     * 與原本「認到翻符或役滿欄位就多留 8 tick、多響一聲」的節奏完全相同。
     */
    @Test
    fun `the riichi template keeps its existing rhythm`() {
        val layout = builtInLayout(RiichiWinSettlementTemplates.RIICHI_KEY)

        assertEquals(8, WinSettlementRevealTimeline.scoreRevealDelayTicks(layout))
        assertEquals(listOf(0), WinSettlementRevealTimeline.lineRevealDelaysTicks(layout, setOf(hanFuId)))
        assertEquals(listOf(0), WinSettlementRevealTimeline.lineRevealDelaysTicks(layout, setOf(yakumanTotalId)))
    }

    /** 通用模板沒有排在逐條揭示之後的內容，節奏與原本相同。 */
    @Test
    fun `the generic template adds no time`() {
        val layout = builtInLayout(BuiltInWinSettlementTemplateKeys.GENERIC)

        assertEquals(0, WinSettlementRevealTimeline.scoreRevealDelayTicks(layout))
        assertEquals(emptyList(), WinSettlementRevealTimeline.lineRevealDelaysTicks(layout, emptySet()))
    }
}
