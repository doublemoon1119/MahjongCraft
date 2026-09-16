package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoundPreparationPromptDto
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證精簡 HUD 的顯示決策與版位。 */
class CompactDecisionHudTest {
    /** 沒有 prompt 時只顯示倒數。 */
    @Test
    fun `shows only the timer without a prompt`() {
        assertEquals(CompactDecisionHudContent.TimerOnly, content(prompt = null))
    }

    /** 操作介面還開著時不提醒重開。 */
    @Test
    fun `shows no reminder while the screen was never dismissed`() {
        assertEquals(CompactDecisionHudContent.TimerOnly, content(dismissedDecisionKey = null))
    }

    /** 收起仍然有效的操作介面後提醒可以重開。 */
    @Test
    fun `reminds the player after dismissing an interactive prompt`() {
        assertEquals(CompactDecisionHudContent.ReopenReminder, content(dismissedDecisionKey = DECISION_KEY))
    }

    /** 收起的是上一個決策的畫面時不提醒。 */
    @Test
    fun `shows no reminder for a dismissed key of another decision`() {
        assertEquals(CompactDecisionHudContent.TimerOnly, content(dismissedDecisionKey = "other-decision"))
    }

    /** 沒有任何可選內容的 prompt 不提醒重開。 */
    @Test
    fun `shows no reminder for a prompt with nothing to choose`() {
        assertEquals(
            CompactDecisionHudContent.TimerOnly,
            content(prompt = PlayerDecisionPromptDto(decisionKey = DECISION_KEY), dismissedDecisionKey = DECISION_KEY),
        )
    }

    /** 只有開局準備的 prompt 仍算需要玩家選擇。 */
    @Test
    fun `treats a preparation only prompt as interactive`() {
        val prompt = PlayerDecisionPromptDto(decisionKey = DECISION_KEY, preparation = RoundPreparationPromptDto.Confirmation)

        assertTrue(prompt.isInteractive)
    }

    /** 玩家已進入實體牌操作模式時不提醒重開。 */
    @Test
    fun `shows no reminder once a physical selection is active`() {
        assertEquals(
            CompactDecisionHudContent.TimerOnly,
            content(dismissedDecisionKey = DECISION_KEY, isPhysicalSelectionActive = true),
        )
    }

    /** 進行中的選牌進度優先於重開提醒。 */
    @Test
    fun `prefers the selection progress over the reminder`() {
        val content = content(
            dismissedDecisionKey = DECISION_KEY,
            tileSelectionProgress = DecisionTileSelectionState.Progress(1, 2..3),
        )

        assertEquals(DecisionTileSelectionState.Progress(1, 2..3), assertIs<CompactDecisionHudContent.TileSelection>(content).progress)
    }

    /** 只有倒數時群組維持倒數本身的高度。 */
    @Test
    fun `keeps the group at the timer height for the timer alone`() {
        assertEquals(CompactDecisionHudLayout.TIMER_HEIGHT, layout(expanded = false).groupHeight)
    }

    /** 有提示文字時群組展開。 */
    @Test
    fun `expands the group for the hint lines`() {
        assertEquals(CompactDecisionHudLayout.EXPANDED_HEIGHT, layout(expanded = true).groupHeight)
    }

    /** 倒數固定貼齊群組下緣，展開與否都一樣。 */
    @Test
    fun `anchors the timer to the bottom of the group`() {
        val collapsed = layout(expanded = false)
        val expanded = layout(expanded = true)

        assertEquals(collapsed.groupTop + collapsed.groupHeight, collapsed.timerTop + CompactDecisionHudLayout.TIMER_HEIGHT)
        assertEquals(expanded.groupTop + expanded.groupHeight, expanded.timerTop + CompactDecisionHudLayout.TIMER_HEIGHT)
    }

    /** 提示文字由群組上緣往下排，第二行接在第一行之後。 */
    @Test
    fun `stacks the hint lines from the top of the group`() {
        val layout = layout(expanded = true)

        assertEquals(layout.groupTop + CompactDecisionHudLayout.TEXT_LINE_HEIGHT, layout.detailTextTop)
        assertTrue(layout.detailTextTop < layout.timerTop)
    }

    /** 畫面比群組固定寬度還窄時，群組讓給畫面寬度。 */
    @Test
    fun `shrinks the group to a narrow screen`() {
        assertEquals(120, layout(expanded = true, screenWidth = 120).groupWidth)
    }

    /** 位置比例 1 讓群組貼齊畫面右下角。 */
    @Test
    fun `anchors the group to the bottom right at ratio one`() {
        val layout = layout(expanded = true, ratioX = 1.0, ratioY = 1.0)

        assertEquals(layout.screenWidth - layout.groupWidth, layout.groupLeft)
        assertEquals(layout.screenHeight - layout.groupHeight, layout.groupTop)
    }

    /** 文字以群組水平中心對齊。 */
    @Test
    fun `centres the text within the group`() {
        val layout = layout(expanded = true)

        assertEquals(layout.groupLeft + layout.groupWidth / 2, layout.centerX)
    }

    /** 還沒選滿最低張數時提示還缺幾張。 */
    @Test
    fun `asks for the missing tiles below the minimum`() {
        val content = assertIs<TranslatableTextContent>(tileSelectionDetailText(DecisionTileSelectionState.Progress(1, 3..4)).content)

        assertEquals("mahjongcraft.hud.tile_selection_need_more", content.key)
        assertEquals(listOf(2, 1, 4), content.args.toList())
    }

    /** 落在合法範圍內時提示可以確認。 */
    @Test
    fun `says the selection is ready inside the valid range`() {
        val content = assertIs<TranslatableTextContent>(tileSelectionDetailText(DecisionTileSelectionState.Progress(3, 2..4)).content)

        assertEquals("mahjongcraft.hud.tile_selection_ready", content.key)
        assertEquals(listOf(3, 4), content.args.toList())
    }

    /** 剛好選滿上限時同樣只提示可以確認。 */
    @Test
    fun `says the selection is ready at the maximum`() {
        val content = assertIs<TranslatableTextContent>(tileSelectionDetailText(DecisionTileSelectionState.Progress(4, 2..4)).content)

        assertEquals("mahjongcraft.hud.tile_selection_ready", content.key)
    }

    /** 組出顯示決策。 */
    private fun content(
        prompt: PlayerDecisionPromptDto? = interactivePrompt(),
        dismissedDecisionKey: String? = null,
        isPhysicalSelectionActive: Boolean = false,
        tileSelectionProgress: DecisionTileSelectionState.Progress? = null,
    ) = compactDecisionHudContent(prompt, dismissedDecisionKey, isPhysicalSelectionActive, tileSelectionProgress)

    /** 建立含一個動作候選的 prompt。 */
    private fun interactivePrompt() = PlayerDecisionPromptDto(
        decisionKey = DECISION_KEY,
        actions = listOf(PlayerDecisionActionDto(token = "token-1", actionId = "mahjongcraft:ron")),
    )

    /** 建立測試用的版位。 */
    private fun layout(
        expanded: Boolean,
        screenWidth: Int = 854,
        screenHeight: Int = 480,
        ratioX: Double = 0.5,
        ratioY: Double = 0.5,
    ) = CompactDecisionHudLayout(screenWidth, screenHeight, ratioX, ratioY, expanded)

    private companion object {
        const val DECISION_KEY = "decision-1"
    }
}
