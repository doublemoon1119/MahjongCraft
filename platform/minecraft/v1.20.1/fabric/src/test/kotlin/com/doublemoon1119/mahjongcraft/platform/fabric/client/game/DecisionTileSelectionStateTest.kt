package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionTileSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionKindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoundPreparationPromptDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證以實體手牌選牌的本機狀態機。 */
class DecisionTileSelectionStateTest {
    private val state = DecisionTileSelectionState()

    /** 只需選一張時不請求確認面板。 */
    @Test
    fun `requests no confirm panel for a single tile preparation`() {
        assertNull(state.beginPreparation(preparationPrompt(minCount = 1, maxCount = 1)))
    }

    /** 需要選多張時請求確認面板，且 preparation 不帶動作 token。 */
    @Test
    fun `requests a confirm panel without a token for a multi tile preparation`() {
        val request = state.beginPreparation(preparationPrompt(minCount = 2, maxCount = 3))

        assertNull(assertNotNullRequest(request).token)
    }

    /** 動作選牌的確認面板請求帶著該動作的 token。 */
    @Test
    fun `requests a confirm panel carrying the action token`() {
        val prompt = actionPrompt(minCount = 2, maxCount = 2)

        val request = state.beginAction(prompt.actions.single())

        assertEquals(ACTION_TOKEN, assertNotNullRequest(request).token)
    }

    /** 不需要選牌的動作不請求確認面板。 */
    @Test
    fun `requests no confirm panel for an action without a tile selection`() {
        assertNull(state.beginAction(PlayerDecisionActionDto(ACTION_TOKEN, "mahjongcraft:pon")))
    }

    /** 沒有進入任何選牌情境時，右鍵手牌不被消化。 */
    @Test
    fun `ignores a tile outside any selection`() {
        assertEquals(
            DecisionTileSelectionState.Outcome.Ignored,
            state.toggle(preparationPrompt(), TILE_A, isActionSelectionActive = false),
        )
    }

    /** 只需選一張時，選中合法牌立即送出。 */
    @Test
    fun `submits immediately when only one tile is needed`() {
        val prompt = preparationPrompt(minCount = 1, maxCount = 1)
        state.beginPreparation(prompt)

        val selection = consumedSelection(state.toggle(prompt, TILE_A, isActionSelectionActive = false))

        assertEquals(PlayerDecisionSelectionKindDto.PREPARATION_TILES, selection?.kind)
        assertNull(selection?.token)
        assertEquals(listOf(TILE_A), selection?.tileIds)
    }

    /** 需要選多張時，即使剛好選滿也要等確認面板。 */
    @Test
    fun `waits for the confirm panel even after reaching the maximum`() {
        val prompt = preparationPrompt(minCount = 2, maxCount = 2)
        state.beginPreparation(prompt)

        assertNull(consumedSelection(state.toggle(prompt, TILE_A, isActionSelectionActive = false)))
        assertNull(consumedSelection(state.toggle(prompt, TILE_B, isActionSelectionActive = false)))
        assertEquals(2, state.progress(prompt, isActionSelectionActive = false)?.selectedCount)
    }

    /** 選滿後右鍵另一張尚未選取的牌沒有反應，但仍消化互動。 */
    @Test
    fun `keeps the selection unchanged when the maximum is already reached`() {
        val prompt = preparationPrompt(minCount = 1, maxCount = 2)
        state.beginPreparation(prompt)
        state.toggle(prompt, TILE_A, isActionSelectionActive = false)
        state.toggle(prompt, TILE_B, isActionSelectionActive = false)

        val outcome = state.toggle(prompt, TILE_C, isActionSelectionActive = false)

        assertIs<DecisionTileSelectionState.Outcome.Consumed>(outcome)
        assertEquals(setOf(TILE_A, TILE_B), state.highlightedTileIds())
    }

    /** 取消一張後才能再選別的。 */
    @Test
    fun `allows another tile after deselecting one`() {
        val prompt = preparationPrompt(minCount = 1, maxCount = 2)
        state.beginPreparation(prompt)
        state.toggle(prompt, TILE_A, isActionSelectionActive = false)
        state.toggle(prompt, TILE_B, isActionSelectionActive = false)

        state.toggle(prompt, TILE_A, isActionSelectionActive = false)
        state.toggle(prompt, TILE_C, isActionSelectionActive = false)

        assertEquals(setOf(TILE_B, TILE_C), state.highlightedTileIds())
    }

    /** 非候選牌不改變選取，但仍消化互動，避免右鍵穿透成普通出牌。 */
    @Test
    fun `consumes an ineligible tile without selecting it`() {
        val prompt = preparationPrompt(minCount = 1, maxCount = 2)
        state.beginPreparation(prompt)

        val outcome = state.toggle(prompt, "tile-outside", isActionSelectionActive = false)

        assertIs<DecisionTileSelectionState.Outcome.Consumed>(outcome)
        assertEquals(emptySet(), state.highlightedTileIds())
    }

    /** 動作選牌失效時，右鍵手牌不被消化。 */
    @Test
    fun `ignores an action tile while the action selection is inactive`() {
        val prompt = actionPrompt(minCount = 1, maxCount = 1)
        state.beginAction(prompt.actions.single())

        assertEquals(
            DecisionTileSelectionState.Outcome.Ignored,
            state.toggle(prompt, TILE_A, isActionSelectionActive = false),
        )
    }

    /** 動作選牌只需一張時，選中即以 ACTION 種類與 token 送出。 */
    @Test
    fun `submits an action tile selection with its token`() {
        val prompt = actionPrompt(minCount = 1, maxCount = 1)
        state.beginAction(prompt.actions.single())

        val selection = consumedSelection(state.toggle(prompt, TILE_A, isActionSelectionActive = true))

        assertEquals(PlayerDecisionSelectionKindDto.ACTION, selection?.kind)
        assertEquals(ACTION_TOKEN, selection?.token)
        assertEquals(listOf(TILE_A), selection?.tileIds)
    }

    /** 確認面板在已選數量落在合法範圍內時送出。 */
    @Test
    fun `submits on confirm once the count is within range`() {
        val prompt = preparationPrompt(minCount = 2, maxCount = 3)
        state.beginPreparation(prompt)
        state.toggle(prompt, TILE_A, isActionSelectionActive = false)
        state.toggle(prompt, TILE_B, isActionSelectionActive = false)

        val selection = consumedSelection(state.confirm(prompt, isActionSelectionActive = false))

        assertEquals(listOf(TILE_A, TILE_B), selection?.tileIds)
    }

    /** 未達最低張數時確認面板不送出，且保留選取讓玩家繼續選。 */
    @Test
    fun `holds the selection when confirm happens below the minimum`() {
        val prompt = preparationPrompt(minCount = 2, maxCount = 3)
        state.beginPreparation(prompt)
        state.toggle(prompt, TILE_A, isActionSelectionActive = false)

        assertNull(consumedSelection(state.confirm(prompt, isActionSelectionActive = false)))
        assertEquals(setOf(TILE_A), state.highlightedTileIds())
    }

    /** 沒有進行中的選牌時，確認面板不被消化。 */
    @Test
    fun `ignores confirm outside any selection`() {
        assertEquals(
            DecisionTileSelectionState.Outcome.Ignored,
            state.confirm(preparationPrompt(), isActionSelectionActive = false),
        )
    }

    /** 選取順序決定送出的牌張順序。 */
    @Test
    fun `submits the tiles in selection order`() {
        val prompt = preparationPrompt(minCount = 2, maxCount = 3)
        state.beginPreparation(prompt)
        state.toggle(prompt, TILE_C, isActionSelectionActive = false)
        state.toggle(prompt, TILE_A, isActionSelectionActive = false)

        assertEquals(listOf(TILE_C, TILE_A), consumedSelection(state.confirm(prompt, isActionSelectionActive = false))?.tileIds)
    }

    /** 進度回報已選數量與合法範圍。 */
    @Test
    fun `reports the progress of the active selection`() {
        val prompt = preparationPrompt(minCount = 2, maxCount = 3)
        state.beginPreparation(prompt)
        state.toggle(prompt, TILE_A, isActionSelectionActive = false)

        assertEquals(DecisionTileSelectionState.Progress(1, 2..3), state.progress(prompt, isActionSelectionActive = false))
    }

    /** 沒有進行中的選牌時沒有進度。 */
    @Test
    fun `reports no progress outside any selection`() {
        assertNull(state.progress(preparationPrompt(), isActionSelectionActive = false))
    }

    /** 權威 prompt 換成另一個決策時丟棄 preparation 選取。 */
    @Test
    fun `drops the preparation selection when the decision changes`() {
        val prompt = preparationPrompt(minCount = 1, maxCount = 2)
        state.beginPreparation(prompt)
        state.toggle(prompt, TILE_A, isActionSelectionActive = false)

        state.syncTo("other-decision", isActionSelectionActive = false)

        assertEquals(emptySet(), state.highlightedTileIds())
        assertNull(state.progress(prompt, isActionSelectionActive = false))
    }

    /** 同一個決策的週期更新不影響進行中的選取。 */
    @Test
    fun `keeps the selection across updates of the same decision`() {
        val prompt = preparationPrompt(minCount = 1, maxCount = 2)
        state.beginPreparation(prompt)
        state.toggle(prompt, TILE_A, isActionSelectionActive = false)

        state.syncTo(DECISION_KEY, isActionSelectionActive = false)

        assertEquals(setOf(TILE_A), state.highlightedTileIds())
    }

    /** 動作選牌失效時丟棄動作選取。 */
    @Test
    fun `drops the action selection once it becomes inactive`() {
        val prompt = actionPrompt(minCount = 1, maxCount = 2)
        state.beginAction(prompt.actions.single())
        state.toggle(prompt, TILE_A, isActionSelectionActive = true)

        state.syncTo(DECISION_KEY, isActionSelectionActive = false)

        assertEquals(emptySet(), state.highlightedTileIds())
        assertNull(state.activeActionToken)
    }

    /** 直接出牌模式只對建立它的決策生效。 */
    @Test
    fun `allows a direct discard only for its own decision`() {
        state.beginDirectDiscard(DECISION_KEY)

        assertTrue(state.allowsDirectDiscard(DECISION_KEY))
        assertFalse(state.allowsDirectDiscard("other-decision"))
    }

    /** 丟棄選牌情境時保留直接出牌模式。 */
    @Test
    fun `keeps the direct discard mode when only selections are dropped`() {
        val prompt = preparationPrompt(minCount = 1, maxCount = 2)
        state.beginPreparation(prompt)
        state.toggle(prompt, TILE_A, isActionSelectionActive = false)
        state.beginDirectDiscard(DECISION_KEY)

        state.clearSelections()

        assertEquals(emptySet(), state.highlightedTileIds())
        assertTrue(state.allowsDirectDiscard(DECISION_KEY))
    }

    /** 完整清除同時丟棄選牌情境與直接出牌模式。 */
    @Test
    fun `drops every mode on a full clear`() {
        val prompt = preparationPrompt(minCount = 1, maxCount = 2)
        state.beginPreparation(prompt)
        state.toggle(prompt, TILE_A, isActionSelectionActive = false)
        state.beginDirectDiscard(DECISION_KEY)

        state.clear()

        assertEquals(emptySet(), state.highlightedTileIds())
        assertFalse(state.allowsDirectDiscard(DECISION_KEY))
    }

    /** 三種實體牌操作模式都算已進入實體選擇階段。 */
    @Test
    fun `treats every physical mode as an active physical selection`() {
        assertFalse(state.isPhysicalSelectionActive(DECISION_KEY, isActionSelectionActive = false))
        assertTrue(state.isPhysicalSelectionActive(DECISION_KEY, isActionSelectionActive = true))

        state.beginPreparation(preparationPrompt())
        assertTrue(state.isPhysicalSelectionActive(DECISION_KEY, isActionSelectionActive = false))

        state.clearSelections()
        state.beginDirectDiscard(DECISION_KEY)
        assertTrue(state.isPhysicalSelectionActive(DECISION_KEY, isActionSelectionActive = false))
    }

    /** 進入新的選牌情境時丟棄上一輪殘留的選取。 */
    @Test
    fun `starts a new selection empty`() {
        val prompt = preparationPrompt(minCount = 1, maxCount = 2)
        state.beginPreparation(prompt)
        state.toggle(prompt, TILE_A, isActionSelectionActive = false)

        state.beginPreparation(prompt)

        assertEquals(emptySet(), state.highlightedTileIds())
    }

    /** 取出必定存在的確認面板請求。 */
    private fun assertNotNullRequest(request: DecisionTileSelectionState.BeginRequest?) = assertIs<DecisionTileSelectionState.BeginRequest>(request)

    /** 取出已消化互動所附帶的最終選擇。 */
    private fun consumedSelection(outcome: DecisionTileSelectionState.Outcome) = assertIs<DecisionTileSelectionState.Outcome.Consumed>(outcome).selection

    /** 建立需要選取實體手牌的 preparation prompt。 */
    private fun preparationPrompt(minCount: Int = 1, maxCount: Int = 1) = PlayerDecisionPromptDto(
        decisionKey = DECISION_KEY,
        preparation = RoundPreparationPromptDto.TileSelection(
            eligibleTileIds = listOf(TILE_A, TILE_B, TILE_C),
            minCount = minCount,
            maxCount = maxCount,
        ),
    )

    /** 建立帶選牌需求的動作 prompt。 */
    private fun actionPrompt(minCount: Int, maxCount: Int) = PlayerDecisionPromptDto(
        decisionKey = DECISION_KEY,
        actions = listOf(
            PlayerDecisionActionDto(
                token = ACTION_TOKEN,
                actionId = "mahjongcraft:riichi",
                tileSelection = PlayerDecisionActionTileSelectionDto(
                    eligibleTileIds = listOf(TILE_A, TILE_B, TILE_C),
                    minCount = minCount,
                    maxCount = maxCount,
                ),
            ),
        ),
    )

    private companion object {
        const val DECISION_KEY = "decision-1"
        const val ACTION_TOKEN = "token-1"
        const val TILE_A = "tile-a"
        const val TILE_B = "tile-b"
        const val TILE_C = "tile-c"
    }
}
