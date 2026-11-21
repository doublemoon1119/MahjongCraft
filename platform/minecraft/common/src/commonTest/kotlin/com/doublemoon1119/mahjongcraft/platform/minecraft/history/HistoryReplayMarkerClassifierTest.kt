package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryActionTypeKeys
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionReplayMarker
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.MinecraftKanActionTokenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.BundledTaiwanMinecraftExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證牌譜交易的回放標記分類。 */
class HistoryReplayMarkerClassifierTest {
    /** 內建規則登記好的動作用語。 */
    private val classifier = HistoryReplayMarkerClassifier(
        actionVocabulary = GameActionVocabularyRegistryImpl().apply {
            BuiltInMinecraftMahjongExtension.registerGameActionVocabulary(this)
            BundledRiichiMinecraftExtension.registerGameActionVocabulary(this)
            BundledTaiwanMinecraftExtension.registerGameActionVocabulary(this)
        },
    )

    /** 吃、碰、各種槓算副露，榮和與自摸算和牌，宣告流局算宣告，摸牌與打牌不標。 */
    @Test
    fun `core actions are classified by their kind`() {
        listOf(HistoryActionTypeKeys.CHI, HistoryActionTypeKeys.PON, HistoryActionTypeKeys.KAN, MinecraftKanActionTokenKeys.CLOSED).forEach {
            assertEquals(GameActionReplayMarker.MELD, classify(BuiltInRuleModuleIds.RIICHI, action(it)))
        }
        listOf(HistoryActionTypeKeys.RON, HistoryActionTypeKeys.TSUMO).forEach {
            assertEquals(GameActionReplayMarker.WIN, classify(BuiltInRuleModuleIds.TAIWAN, action(it)))
        }
        assertEquals(GameActionReplayMarker.DECLARATION, classify(BuiltInRuleModuleIds.RIICHI, action(HistoryActionTypeKeys.EXHAUSTIVE_DRAW)))
        assertNull(classify(BuiltInRuleModuleIds.RIICHI, action(HistoryActionTypeKeys.DRAW), action(HistoryActionTypeKeys.DISCARD)))
    }

    /** 立直與聽牌宣告是宣告；拔北與補花把牌亮到旁邊再補摸，算鳴牌；沒有登記的動作不標。 */
    @Test
    fun `rule actions use the marker their rule registered`() {
        assertEquals(GameActionReplayMarker.DECLARATION, classify(BuiltInRuleModuleIds.RIICHI, extension(RiichiGameAction.Riichi.id)))
        assertEquals(GameActionReplayMarker.DECLARATION, classify(BuiltInRuleModuleIds.TAIWAN, extension(TaiwanGameAction.DeclareReady.id)))
        assertEquals(GameActionReplayMarker.MELD, classify(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, extension(RiichiGameAction.PullNorth.id)))
        assertEquals(GameActionReplayMarker.MELD, classify(BuiltInRuleModuleIds.TAIWAN, extension(TaiwanGameAction.RevealFlower.id)))
        assertNull(classify(BuiltInRuleModuleIds.TAIWAN, extension("example:unregistered")))
    }

    /** 同一筆交易有多個要標記的動作時取最重要的。 */
    @Test
    fun `the most important action of a transaction wins`() {
        assertEquals(GameActionReplayMarker.WIN, classify(BuiltInRuleModuleIds.RIICHI, action(HistoryActionTypeKeys.KAN), action(HistoryActionTypeKeys.TSUMO)))
    }

    /** 宣告榮和後，其他玩家跳過的那筆交易同時記下裁定結果，只有宣告榮和的那筆被標記。 */
    @Test
    fun `a pass that completes the wait is not marked by the resolution`() {
        val markers = classifier.markersOf(
            ruleModuleId = BuiltInRuleModuleIds.RIICHI,
            transactions = transactions(
                listOf(action(HistoryActionTypeKeys.DISCARD, seat = 0)),
                listOf(action(HistoryActionTypeKeys.RON, seat = 1)),
                listOf(action(HistoryActionTypeKeys.PASS, seat = 2), resolution(HistoryActionTypeKeys.RON, seat = 1)),
            ),
        )

        assertEquals(listOf(null, GameActionReplayMarker.WIN, null), markers)
    }

    /** 被榮和蓋掉的碰沒有成立，不標記。 */
    @Test
    fun `a declaration overridden by a higher priority one is not marked`() {
        val markers = classifier.markersOf(
            ruleModuleId = BuiltInRuleModuleIds.RIICHI,
            transactions = transactions(
                listOf(action(HistoryActionTypeKeys.DISCARD, seat = 0)),
                listOf(action(HistoryActionTypeKeys.PON, seat = 2)),
                listOf(action(HistoryActionTypeKeys.RON, seat = 1), resolution(HistoryActionTypeKeys.RON, seat = 1)),
            ),
        )

        assertEquals(listOf(null, null, GameActionReplayMarker.WIN), markers)
    }

    /** 多家同時榮和時裁定結果沒有單一玩家，每位宣告榮和的玩家都標記。 */
    @Test
    fun `every winner of a multiple win is marked`() {
        val markers = classifier.markersOf(
            ruleModuleId = BuiltInRuleModuleIds.RIICHI,
            transactions = transactions(
                listOf(action(HistoryActionTypeKeys.DISCARD, seat = 0)),
                listOf(action(HistoryActionTypeKeys.RON, seat = 1)),
                listOf(action(HistoryActionTypeKeys.RON, seat = 2), resolution(HistoryActionTypeKeys.RON, seat = null)),
            ),
        )

        assertEquals(listOf(null, GameActionReplayMarker.WIN, GameActionReplayMarker.WIN), markers)
    }

    /** 加槓等待搶槓：沒被搶時標記槓，被搶槓時只標記榮和。 */
    @Test
    fun `a robbable kan is marked only when it is not robbed`() {
        val kept = classifier.markersOf(
            ruleModuleId = BuiltInRuleModuleIds.RIICHI,
            transactions = transactions(
                listOf(action(HistoryActionTypeKeys.DRAW, seat = 0), action(MinecraftKanActionTokenKeys.ADDED, seat = 0)),
                listOf(action(HistoryActionTypeKeys.PASS, seat = 1), resolution(MinecraftKanActionTokenKeys.ADDED, seat = 0)),
            ),
        )
        val robbed = classifier.markersOf(
            ruleModuleId = BuiltInRuleModuleIds.RIICHI,
            transactions = transactions(
                listOf(action(HistoryActionTypeKeys.DRAW, seat = 0), action(MinecraftKanActionTokenKeys.ADDED, seat = 0)),
                listOf(action(HistoryActionTypeKeys.RON, seat = 1), resolution(HistoryActionTypeKeys.RON, seat = 1)),
            ),
        )

        assertEquals(listOf(GameActionReplayMarker.MELD, null), kept)
        assertEquals(listOf(null, GameActionReplayMarker.WIN), robbed)
    }

    /** 立直在打牌之前宣告，不屬於打牌後的等待，即使那張牌被碰仍標記。 */
    @Test
    fun `declarations before the discard keep their marker`() {
        val markers = classifier.markersOf(
            ruleModuleId = BuiltInRuleModuleIds.RIICHI,
            transactions = transactions(
                listOf(extension(RiichiGameAction.Riichi.id), action(HistoryActionTypeKeys.DISCARD, seat = 0)),
                listOf(action(HistoryActionTypeKeys.PON, seat = 1), resolution(HistoryActionTypeKeys.PON, seat = 1)),
            ),
        )

        assertEquals(listOf(GameActionReplayMarker.DECLARATION, GameActionReplayMarker.MELD), markers)
    }

    /** 還沒讀到裁定結果時，宣告先依本身的動作標記。 */
    @Test
    fun `declarations without a loaded resolution use their own marker`() {
        val markers = classifier.markersOf(
            ruleModuleId = BuiltInRuleModuleIds.RIICHI,
            transactions = transactions(
                listOf(action(HistoryActionTypeKeys.DISCARD, seat = 0)),
                listOf(action(HistoryActionTypeKeys.PON, seat = 1)),
            ),
        )

        assertEquals(listOf(null, GameActionReplayMarker.MELD), markers)
    }

    /**
     * 分類由指定事實組成的單一交易。
     *
     * @param ruleModuleId 規則模組 ID。
     * @param facts 交易中的事實。
     * @return 標記種類。
     */
    private fun classify(ruleModuleId: String, vararg facts: HistoryReplayFactDto): GameActionReplayMarker? = classifier.markersOf(ruleModuleId = ruleModuleId, transactions = transactions(facts.toList())).single()

    /**
     * 依序建立牌譜交易。
     *
     * @param facts 每筆交易的事實。
     * @return 編號從 0 開始的交易。
     */
    private fun transactions(vararg facts: List<HistoryReplayFactDto>): List<HistoryReplayTransactionDto> = facts.mapIndexed { index, transactionFacts ->
        HistoryReplayTransactionDto(index = index, occurredAtEpochMillis = 0, isOpening = false, declaredTileCountAfter = 0, facts = transactionFacts)
    }

    /**
     * 建立一次等待回應的裁定結果。
     *
     * @param actionType 成立的動作種類。
     * @param seat 成立動作的玩家座位；沒有單一玩家時為 null。
     * @return 裁定結果事實。
     */
    private fun resolution(actionType: String, seat: Int?): HistoryReplayFactDto = HistoryReplayFactDto.Reaction(
        typeKey = "reaction",
        actionType = actionType,
        resolvedActorSeat = seat,
    )

    /**
     * 建立一個核心動作事實。
     *
     * @param actionType 保存格式中的動作種類。
     * @param seat 執行動作的玩家座位。
     * @return 動作事實。
     */
    private fun action(actionType: String, seat: Int = 0): HistoryReplayFactDto = HistoryReplayFactDto.KnownAction(
        typeKey = "known_action",
        actorSeat = seat,
        actionType = actionType,
        directTiles = emptyList(),
        revealedTiles = emptyList(),
        extensionTypeId = null,
    )

    /**
     * 建立一個規則擴充動作事實。
     *
     * @param extensionTypeId 擴充動作 ID。
     * @return 動作事實。
     */
    private fun extension(extensionTypeId: String): HistoryReplayFactDto = HistoryReplayFactDto.KnownAction(
        typeKey = "known_action",
        actorSeat = 0,
        actionType = HistoryActionTypeKeys.EXTENSION,
        directTiles = emptyList(),
        revealedTiles = emptyList(),
        extensionTypeId = extensionTypeId,
    )
}
