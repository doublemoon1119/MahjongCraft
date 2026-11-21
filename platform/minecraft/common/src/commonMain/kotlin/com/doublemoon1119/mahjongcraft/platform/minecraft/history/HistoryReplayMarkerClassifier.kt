package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryActionTypeKeys
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionReplayMarker
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.MinecraftKanActionTokenKeys

/**
 * 判斷一筆牌譜交易在回放時的標記種類。
 *
 * 吃、碰、槓算副露，榮和與自摸算和牌，玩家宣告的流局算宣告，這些核心動作在每種規則都一樣；
 * 規則擴充的動作讀該規則動作用語登記的 [GameActionVocabulary.replayMarker]，
 * 沒有登記時不標記。
 *
 * @property actionVocabulary 規則動作用語。
 */
class HistoryReplayMarkerClassifier(private val actionVocabulary: GameActionVocabularyRegistry) {
    /**
     * 取得一筆交易的標記種類；有多個動作時取最重要的一種。
     *
     * @param ruleModuleId 對局的規則模組 ID；不明時只看核心動作與中立預設。
     * @param transaction 牌譜交易。
     * @return 標記種類；不需要標記時為 null。
     */
    fun markerOf(ruleModuleId: String?, transaction: HistoryReplayTransactionDto): GameActionReplayMarker? = transaction.facts
        .mapNotNull { fact -> markerOf(ruleModuleId, fact) }
        .maxOrNull()

    /**
     * 取得單一事實的標記種類。
     *
     * @param ruleModuleId 對局的規則模組 ID。
     * @param fact 交易中的一個事實。
     * @return 標記種類；不是需要標記的動作時為 null。
     */
    private fun markerOf(ruleModuleId: String?, fact: HistoryReplayFactDto): GameActionReplayMarker? = when (fact) {
        is HistoryReplayFactDto.KnownAction -> markerOf(ruleModuleId, fact.actionType, fact.extensionTypeId)
        is HistoryReplayFactDto.Reaction -> fact.actionType?.let { markerOf(ruleModuleId, it, extensionTypeId = null) }
        else -> null
    }

    /**
     * 依動作種類取得標記種類。
     *
     * @param ruleModuleId 對局的規則模組 ID。
     * @param actionType 保存格式中的動作種類。
     * @param extensionTypeId 規則擴充動作的 ID；核心動作為 null。
     * @return 標記種類；不需要標記時為 null。
     */
    private fun markerOf(ruleModuleId: String?, actionType: String, extensionTypeId: String?): GameActionReplayMarker? = when (actionType) {
        in MELD_ACTION_TYPES -> GameActionReplayMarker.MELD
        in WIN_ACTION_TYPES -> GameActionReplayMarker.WIN
        HistoryActionTypeKeys.EXHAUSTIVE_DRAW -> GameActionReplayMarker.DECLARATION
        HistoryActionTypeKeys.EXTENSION -> extensionTypeId?.let { actionVocabulary.find(ruleModuleId, it)?.replayMarker }
        else -> null
    }

    /** 核心動作的種類代號。 */
    private companion object {
        /** 保存格式與內建動作 ID 中代表副露的動作。 */
        val MELD_ACTION_TYPES: Set<String> = setOf(
            HistoryActionTypeKeys.CHI,
            HistoryActionTypeKeys.PON,
            HistoryActionTypeKeys.KAN,
            MinecraftKanActionTokenKeys.OPEN,
            MinecraftKanActionTokenKeys.CLOSED,
            MinecraftKanActionTokenKeys.ADDED,
            BuiltInGameActionIds.CHI,
            BuiltInGameActionIds.PON,
            BuiltInGameActionIds.KAN_OPEN,
            BuiltInGameActionIds.KAN_CLOSED,
            BuiltInGameActionIds.KAN_ADDED,
        )

        /** 保存格式與內建動作 ID 中代表和牌的動作。 */
        val WIN_ACTION_TYPES: Set<String> = setOf(
            HistoryActionTypeKeys.RON,
            HistoryActionTypeKeys.TSUMO,
            BuiltInGameActionIds.RON,
            BuiltInGameActionIds.TSUMO,
        )
    }
}
