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

    /** 同一筆交易有多個動作時取最重要的，被鳴牌反應選中的動作也算。 */
    @Test
    fun `the most important action of a transaction wins`() {
        val pon = HistoryReplayFactDto.Reaction(typeKey = "reaction", actionType = HistoryActionTypeKeys.PON, resolvedActorSeat = 1)

        assertEquals(GameActionReplayMarker.WIN, classify(BuiltInRuleModuleIds.RIICHI, pon, action(HistoryActionTypeKeys.RON)))
        assertEquals(GameActionReplayMarker.MELD, classify(BuiltInRuleModuleIds.RIICHI, pon))
    }

    /**
     * 分類由指定事實組成的一筆交易。
     *
     * @param ruleModuleId 規則模組 ID。
     * @param facts 交易中的事實。
     * @return 標記種類。
     */
    private fun classify(ruleModuleId: String, vararg facts: HistoryReplayFactDto): GameActionReplayMarker? = classifier.markerOf(
        ruleModuleId = ruleModuleId,
        transaction = HistoryReplayTransactionDto(index = 0, occurredAtEpochMillis = 0, isOpening = false, declaredTileCountAfter = 0, facts = facts.toList()),
    )

    /**
     * 建立一個核心動作事實。
     *
     * @param actionType 保存格式中的動作種類。
     * @return 動作事實。
     */
    private fun action(actionType: String): HistoryReplayFactDto = HistoryReplayFactDto.KnownAction(
        typeKey = "known_action",
        actorSeat = 0,
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
