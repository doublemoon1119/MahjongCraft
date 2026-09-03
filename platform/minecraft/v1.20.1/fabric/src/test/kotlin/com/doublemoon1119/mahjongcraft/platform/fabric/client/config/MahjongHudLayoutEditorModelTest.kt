package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 驗證 HUD 位置編輯器的拖曳夾限、可調整軸、預覽情境換算與草稿狀態語意。
 *
 * 全部斷言都針對狀態轉換與 bounds 結果，不比對畫面像素，因此不會因為配色或字級調整而失效。
 */
class MahjongHudLayoutEditorModelTest {
    /** 拖曳到畫面外的任何位置後，HUD 四邊都必須仍留在畫面內。 */
    @Test
    fun `dragging beyond any screen edge keeps the hud fully on screen`() {
        HudElement.entries.forEach { element ->
            val dragged = model()
                .selectElement(element)
                .beginDrag(
                    element = element,
                    mouseX = 0.0,
                    mouseY = 0.0,
                    screenWidth = SCREEN_WIDTH,
                    screenHeight = SCREEN_HEIGHT,
                )

            listOf(
                -10_000.0 to -10_000.0,
                10_000.0 to -10_000.0,
                -10_000.0 to 10_000.0,
                10_000.0 to 10_000.0,
            ).forEach { (mouseX, mouseY) ->
                val bounds = dragged
                    .dragTo(
                        mouseX = mouseX,
                        mouseY = mouseY,
                        screenWidth = SCREEN_WIDTH,
                        screenHeight = SCREEN_HEIGHT,
                    )
                    .bounds(
                        element = element,
                        screenWidth = SCREEN_WIDTH,
                        screenHeight = SCREEN_HEIGHT,
                    )

                assertTrue(bounds.left >= 0, "$element left ${bounds.left} escaped the screen")
                assertTrue(bounds.top >= 0, "$element top ${bounds.top} escaped the screen")
                assertTrue(bounds.right <= SCREEN_WIDTH, "$element right ${bounds.right} escaped the screen")
                assertTrue(bounds.bottom <= SCREEN_HEIGHT, "$element bottom ${bounds.bottom} escaped the screen")
            }
        }
    }

    /** 一般倒數與等待提醒兩軸都可調整。 */
    @Test
    fun `compact prompt accepts both horizontal and vertical adjustment`() {
        val initial = model()
        val dragged = initial
            .selectElement(HudElement.COMPACT)
            .beginDrag(
                element = HudElement.COMPACT,
                mouseX = 0.0,
                mouseY = 0.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )
            .dragTo(
                mouseX = 40.0,
                mouseY = 40.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )

        assertNotEquals(initial.draft.compactPromptX, dragged.draft.compactPromptX)
        assertNotEquals(initial.draft.compactPromptY, dragged.draft.compactPromptY)
    }

    /** 固定水平置中的 HUD 只接受垂直調整，且拖曳後仍維持置中。 */
    @Test
    fun `centered huds ignore horizontal dragging and stay centered`() {
        listOf(HudElement.DECISION, HudElement.ANALYSIS).forEach { element ->
            val initial = model().selectElement(element)
            val centeredLeft = initial.bounds(
                element = element,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            ).left

            val dragged = initial
                .beginDrag(
                    element = element,
                    mouseX = 0.0,
                    mouseY = 0.0,
                    screenWidth = SCREEN_WIDTH,
                    screenHeight = SCREEN_HEIGHT,
                )
                .dragTo(
                    mouseX = 600.0,
                    mouseY = 40.0,
                    screenWidth = SCREEN_WIDTH,
                    screenHeight = SCREEN_HEIGHT,
                )

            assertFalse(element.adjustsHorizontally, "$element must not be horizontally adjustable")
            assertEquals(initial.draft.compactPromptX, dragged.draft.compactPromptX)
            assertEquals(
                centeredLeft,
                dragged.bounds(
                    element = element,
                    screenWidth = SCREEN_WIDTH,
                    screenHeight = SCREEN_HEIGHT,
                ).left,
                "$element left edge moved despite being centered",
            )
        }
    }

    /** 拖曳操作面板不得寫入其他 HUD 的位置欄位。 */
    @Test
    fun `dragging one hud leaves the other huds untouched`() {
        val initial = model()
        val dragged = initial
            .beginDrag(
                element = HudElement.DECISION,
                mouseX = 0.0,
                mouseY = 0.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )
            .dragTo(
                mouseX = 100.0,
                mouseY = 100.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )

        assertNotEquals(initial.draft.decisionPanelY, dragged.draft.decisionPanelY)
        assertEquals(initial.draft.compactPromptX, dragged.draft.compactPromptX)
        assertEquals(initial.draft.compactPromptY, dragged.draft.compactPromptY)
        assertEquals(initial.draft.discardAnalysisY, dragged.draft.discardAnalysisY)
    }

    /** 換成更大的預覽情境後，原本合法的位置仍必須被重新限制在畫面內。 */
    @Test
    fun `switching to a larger scenario re-clamps the panel inside the screen`() {
        val narrowScreenWidth = HudPreviewScenario.CALL.width + 40
        val narrowScreenHeight = HudPreviewScenario.CALL.height + 40
        val atBottom = model()
            .beginDrag(
                element = HudElement.DECISION,
                mouseX = 0.0,
                mouseY = 0.0,
                screenWidth = narrowScreenWidth,
                screenHeight = narrowScreenHeight,
            )
            .dragTo(
                mouseX = 10_000.0,
                mouseY = 10_000.0,
                screenWidth = narrowScreenWidth,
                screenHeight = narrowScreenHeight,
            )

        HudPreviewScenario.entries.forEach { scenario ->
            val bounds = atBottom.selectScenario(scenario).bounds(
                element = HudElement.DECISION,
                screenWidth = narrowScreenWidth,
                screenHeight = narrowScreenHeight,
            )

            assertTrue(bounds.left >= 0, "$scenario left ${bounds.left} escaped the screen")
            assertTrue(bounds.top >= 0, "$scenario top ${bounds.top} escaped the screen")
            assertTrue(bounds.right <= narrowScreenWidth, "$scenario right ${bounds.right} escaped the screen")
            assertTrue(bounds.bottom <= narrowScreenHeight, "$scenario bottom ${bounds.bottom} escaped the screen")
        }
    }

    /** 預覽框在極小畫面下仍保留至少一像素，且不超過畫面尺寸。 */
    @Test
    fun `preview size stays positive and bounded on tiny screens`() {
        val size = model().previewSize(
            element = HudElement.DECISION,
            screenWidth = 8,
            screenHeight = 8,
        )

        assertTrue(size.width >= 1)
        assertTrue(size.height >= 1)
        assertTrue(size.width <= 8)
        assertTrue(size.height <= 8)
    }

    /** 其他 HUD 預設隱藏，隱藏時不得攔截拖曳點擊；切成外框後才可被選取。 */
    @Test
    fun `hidden huds cannot be picked up until outline preview is enabled`() {
        assertEquals(HudPreviewVisibility.HIDDEN, model().otherHudVisibility)

        // 先把目前選取的操作面板拖到畫面頂端，避免它的預覽框蓋住待測的一般倒數預覽框。
        val initial = model()
            .beginDrag(
                element = HudElement.DECISION,
                mouseX = 0.0,
                mouseY = 0.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )
            .dragTo(
                mouseX = 0.0,
                mouseY = -10_000.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )
            .endDrag()

        val compactBounds = initial.bounds(
            element = HudElement.COMPACT,
            screenWidth = SCREEN_WIDTH,
            screenHeight = SCREEN_HEIGHT,
        )
        val insideCompactX = compactBounds.left + 1.0
        val insideCompactY = compactBounds.top + 1.0
        assertFalse(
            initial.bounds(
                element = HudElement.DECISION,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            ).contains(insideCompactX, insideCompactY),
            "the probe point must lie outside the selected hud for this test to mean anything",
        )

        assertNull(
            initial.hitTest(
                mouseX = insideCompactX,
                mouseY = insideCompactY,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            ),
        )
        assertEquals(
            HudElement.COMPACT,
            initial.selectVisibility(HudPreviewVisibility.OUTLINE).hitTest(
                mouseX = insideCompactX,
                mouseY = insideCompactY,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            ),
        )
    }

    /** 重疊時目前選取的 HUD 優先取得拖曳焦點。 */
    @Test
    fun `the selected hud wins the hit test when previews overlap`() {
        val overlapping = model()
            .selectVisibility(HudPreviewVisibility.OUTLINE)
            .selectElement(HudElement.ANALYSIS)
        val analysisBounds = overlapping.bounds(
            element = HudElement.ANALYSIS,
            screenWidth = SCREEN_WIDTH,
            screenHeight = SCREEN_HEIGHT,
        )

        assertEquals(
            HudElement.ANALYSIS,
            overlapping.hitTest(
                mouseX = analysisBounds.left + 1.0,
                mouseY = analysisBounds.top + 1.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            ),
        )
    }

    /** 手動隱藏控制項後仍可還原，拖曳期間也會自動收起控制項。 */
    @Test
    fun `controls can always be restored after being hidden`() {
        val visible = model()
        assertTrue(visible.controlsVisible)

        val hidden = visible.withControlsHidden(true)
        assertFalse(hidden.controlsVisible)
        assertTrue(hidden.withControlsHidden(false).controlsVisible)

        val dragging = visible.beginDrag(
            element = HudElement.DECISION,
            mouseX = 0.0,
            mouseY = 0.0,
            screenWidth = SCREEN_WIDTH,
            screenHeight = SCREEN_HEIGHT,
        )
        assertFalse(dragging.controlsVisible)
        assertTrue(dragging.endDrag().controlsVisible)
    }

    /** 未變更時套用與復原不可用；拖曳後兩者才啟用，套用後回到未變更狀態。 */
    @Test
    fun `apply and undo track unsaved changes across a full edit cycle`() {
        val initial = model()
        assertFalse(initial.hasUnsavedChanges)

        val dragged = initial
            .beginDrag(
                element = HudElement.DECISION,
                mouseX = 0.0,
                mouseY = 0.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )
            .dragTo(
                mouseX = 100.0,
                mouseY = 100.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )
        assertTrue(dragged.hasUnsavedChanges)

        val applied = dragged.markApplied()
        assertFalse(applied.hasUnsavedChanges)
        assertEquals(dragged.draft, applied.baseline)
    }

    /** 復原把草稿還原成最近一次套用的配置，而不是預設配置。 */
    @Test
    fun `undo restores the last applied layout rather than the defaults`() {
        val applied = model()
            .beginDrag(
                element = HudElement.DECISION,
                mouseX = 0.0,
                mouseY = 0.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )
            .dragTo(
                mouseX = 100.0,
                mouseY = 100.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )
            .markApplied()

        val undone = applied
            .dragTo(
                mouseX = 300.0,
                mouseY = 300.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )
            .undo()

        assertEquals(applied.baseline, undone.draft)
        assertFalse(undone.hasUnsavedChanges)
        assertNotEquals(MahjongHudLayoutConfig(), undone.draft)
    }

    /** 重設把草稿還原成預設配置，且在已是預設配置時被視為不可用。 */
    @Test
    fun `reset returns the draft to the packaged defaults`() {
        val initial = model()
        assertTrue(initial.isDefault)

        val dragged = initial
            .beginDrag(
                element = HudElement.DECISION,
                mouseX = 0.0,
                mouseY = 0.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )
            .dragTo(
                mouseX = 100.0,
                mouseY = 100.0,
                screenWidth = SCREEN_WIDTH,
                screenHeight = SCREEN_HEIGHT,
            )
        assertFalse(dragged.isDefault)

        val reset = dragged.reset()
        assertTrue(reset.isDefault)
        assertEquals(MahjongHudLayoutConfig(), reset.draft)
    }

    /** 靠近畫面中線時吸附至正中央，超出吸附範圍則保留原值。 */
    @Test
    fun `snapping pulls near-center ratios to the exact center`() {
        assertEquals(0.5, snap(0.5 + MahjongHudLayoutEditorModel.SNAP_THRESHOLD / 2))
        assertEquals(0.5, snap(0.5 - MahjongHudLayoutEditorModel.SNAP_THRESHOLD / 2))
        assertEquals(0.5, snap(0.5))

        val outside = 0.5 + MahjongHudLayoutEditorModel.SNAP_THRESHOLD * 2
        assertEquals(outside, snap(outside))
    }

    /** 建立以預設配置為基準的編輯器狀態。 */
    private fun model(): MahjongHudLayoutEditorModel = MahjongHudLayoutEditorModel(baseline = MahjongHudLayoutConfig())

    private companion object {
        /** 測試使用的最小支援畫面寬度。 */
        const val SCREEN_WIDTH = 854

        /** 測試使用的最小支援畫面高度。 */
        const val SCREEN_HEIGHT = 480
    }
}
