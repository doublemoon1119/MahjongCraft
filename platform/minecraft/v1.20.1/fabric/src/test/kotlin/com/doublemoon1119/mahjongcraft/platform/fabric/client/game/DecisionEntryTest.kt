package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionTileSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoundPreparationPromptDto
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 驗證由權威 prompt 組出的操作卡清單。 */
class DecisionEntryTest {
    /** 動作卡依固定顯示順序排列。 */
    @Test
    fun `orders the action cards by their display priority`() {
        val entries = decisionEntriesFrom(
            promptOf("mahjongcraft:tsumo", "mahjongcraft:kan_open", "mahjongcraft:ron", "mahjongcraft:chi", "mahjongcraft:pon"),
        )

        assertEquals(
            listOf("chi", "pon", "kan_open", "ron", "tsumo").map { "mahjongcraft.hud.action.$it" },
            entries.map { translationKeyOf(it) },
        )
    }

    /** 跳過不佔卡片位置。 */
    @Test
    fun `leaves the pass action out of the cards`() {
        val entries = decisionEntriesFrom(promptOf("mahjongcraft:pass", "mahjongcraft:ron"))

        assertEquals(1, entries.size)
        assertEquals("mahjongcraft.hud.action.ron", translationKeyOf(entries.single()))
    }

    /** 不需選牌的動作點擊後直接送出該候選。 */
    @Test
    fun `submits an action that needs no tile selection`() {
        val entries = decisionEntriesFrom(promptOf("mahjongcraft:ron"))

        val intent = assertIs<DecisionEntryIntent.SubmitAction>(entries.single().intent)
        assertEquals("token-mahjongcraft:ron", intent.token)
    }

    /** 需要選牌的動作點擊後進入實體牌選取模式。 */
    @Test
    fun `starts a tile selection for an action that needs one`() {
        val prompt = PlayerDecisionPromptDto(
            decisionKey = DECISION_KEY,
            actions = listOf(
                PlayerDecisionActionDto(
                    token = "token-riichi",
                    actionId = "mahjongcraft:riichi",
                    tileSelection = PlayerDecisionActionTileSelectionDto(listOf("tile-a"), minCount = 1, maxCount = 1),
                ),
            ),
        )

        val intent = assertIs<DecisionEntryIntent.BeginActionTileSelection>(decisionEntriesFrom(prompt).single().intent)
        assertEquals("token-riichi", intent.action.token)
    }

    /** 動作卡沿用該候選的預覽牌與鳴牌指標索引。 */
    @Test
    fun `carries the preview tiles and the claimed index of its action`() {
        val prompt = PlayerDecisionPromptDto(
            decisionKey = DECISION_KEY,
            actions = listOf(
                PlayerDecisionActionDto(
                    token = "token-chi",
                    actionId = "mahjongcraft:chi",
                    previewTileAssetKeys = listOf("a", "b", "c"),
                    claimedTileIndex = 1,
                ),
            ),
        )

        val entry = decisionEntriesFrom(prompt).single()
        assertEquals(listOf("a", "b", "c"), entry.previewTileAssetKeys)
        assertEquals(1, entry.claimedTileIndex)
        assertEquals(DecisionCard(previewTileCount = 3, hasClaimedTileMarker = true), entry.layoutCard())
    }

    /** 沒有鳴牌指標的卡片不為指標保留高度。 */
    @Test
    fun `marks no claimed tile without an index`() {
        val entry = decisionEntriesFrom(promptOf("mahjongcraft:ron")).single()

        assertNull(entry.claimedTileIndex)
        assertEquals(DecisionCard(previewTileCount = 0, hasClaimedTileMarker = false), entry.layoutCard())
    }

    /** 只需確認的開局準備展開成一張確認卡。 */
    @Test
    fun `builds one card for a preparation confirmation`() {
        val entries = decisionEntriesFrom(preparationPrompt(RoundPreparationPromptDto.Confirmation))

        assertEquals(DecisionEntryIntent.ConfirmPreparation, entries.single().intent)
        assertEquals("mahjongcraft.hud.action.confirm", translationKeyOf(entries.single()))
    }

    /** 單選的開局準備每個選項各一張卡，並保留原順序。 */
    @Test
    fun `builds one card per preparation option`() {
        val entries = decisionEntriesFrom(
            preparationPrompt(RoundPreparationPromptDto.SingleChoice(listOf("mahjongcraft:accept", "mahjongcraft:decline"))),
        )

        assertEquals(
            listOf("mahjongcraft:accept", "mahjongcraft:decline"),
            entries.map { assertIs<DecisionEntryIntent.ChoosePreparationOption>(it.intent).optionId },
        )
    }

    /** 選牌的開局準備展開成一張帶候選牌預覽的卡。 */
    @Test
    fun `builds one card carrying the eligible tiles of a preparation selection`() {
        val entries = decisionEntriesFrom(
            preparationPrompt(
                RoundPreparationPromptDto.TileSelection(
                    eligibleTileIds = listOf("tile-a", "tile-b"),
                    eligibleTileAssetKeys = listOf("asset-a", "asset-b"),
                    minCount = 1,
                    maxCount = 2,
                ),
            ),
        )

        val entry = entries.single()
        assertEquals(DecisionEntryIntent.BeginPreparationTileSelection, entry.intent)
        assertEquals(listOf("asset-a", "asset-b"), entry.previewTileAssetKeys)
    }

    /** 動作卡排在開局準備卡之前。 */
    @Test
    fun `puts the action cards before the preparation card`() {
        val prompt = promptOf("mahjongcraft:ron").copy(preparation = RoundPreparationPromptDto.Confirmation)

        val entries = decisionEntriesFrom(prompt)

        assertIs<DecisionEntryIntent.SubmitAction>(entries.first().intent)
        assertEquals(DecisionEntryIntent.ConfirmPreparation, entries.last().intent)
    }

    /** 沒有動作也沒有開局準備時沒有任何卡片。 */
    @Test
    fun `builds no card for an empty prompt`() {
        assertEquals(emptyList(), decisionEntriesFrom(PlayerDecisionPromptDto(decisionKey = DECISION_KEY)))
    }

    /** 未知的動作 ID 排在內建動作之後並維持原始相對順序。 */
    @Test
    fun `keeps unknown actions last in their original order`() {
        val entries = decisionEntriesFrom(promptOf("example:first", "example:second", "mahjongcraft:ron"))

        assertEquals(
            listOf("mahjongcraft.hud.action.ron", "example:first", "example:second"),
            entries.map { translationKeyOf(it) },
        )
    }

    /** 取出卡片標題的翻譯 key；未知 ID 保留原始字串。 */
    private fun translationKeyOf(entry: DecisionEntry): String = (entry.label.content as? TranslatableTextContent)?.key ?: entry.label.string

    /** 建立只含指定動作的 prompt。 */
    private fun promptOf(vararg actionIds: String) = PlayerDecisionPromptDto(
        decisionKey = DECISION_KEY,
        actions = actionIds.map { PlayerDecisionActionDto(token = "token-$it", actionId = it) },
    )

    /** 建立只含開局準備的 prompt。 */
    private fun preparationPrompt(preparation: RoundPreparationPromptDto) = PlayerDecisionPromptDto(decisionKey = DECISION_KEY, preparation = preparation)

    private companion object {
        const val DECISION_KEY = "decision-1"
    }
}
