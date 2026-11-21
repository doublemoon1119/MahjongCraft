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
 * 判斷牌譜交易在回放時的標記種類。
 *
 * 吃、碰、槓算副露，榮和與自摸算和牌，玩家宣告的流局算宣告，這些核心動作在每種規則都一樣；
 * 規則擴充的動作讀該規則動作用語登記的 [GameActionVocabulary.replayMarker]，
 * 沒有登記時不標記。
 *
 * 標記放在玩家宣告動作的那筆交易。在等待其他玩家回應的期間宣告的動作（例如對捨牌吃碰槓和，或被搶槓的槓），
 * 只有在那次等待的裁定結果選中它時才標記；被更高優先的宣告蓋掉、最後沒有成立的宣告不標記。
 * 裁定結果本身不標記，因此回應者跳過的那筆交易即使同時記下裁定結果，也不會被標記。
 *
 * @property actionVocabulary 規則動作用語。
 */
class HistoryReplayMarkerClassifier(private val actionVocabulary: GameActionVocabularyRegistry) {
    /**
     * 依序取得各筆交易的標記種類；同一筆交易有多個要標記的動作時取最重要的一種。
     *
     * 一次等待回應的裁定結果記在之後的交易，因此要從整串交易判斷；只給出一部分交易、
     * 還沒讀到裁定結果時，宣告先依它本身的動作標記。
     *
     * @param ruleModuleId 對局的規則模組 ID；不明時只看核心動作與中立預設。
     * @param transactions 依發生順序排列的牌譜交易。
     * @return 與 [transactions] 一一對應的標記種類；不需要標記時為 null。
     */
    fun markersOf(ruleModuleId: String?, transactions: List<HistoryReplayTransactionDto>): List<GameActionReplayMarker?> {
        val markers = MutableList<GameActionReplayMarker?>(transactions.size) { null }
        // 由後往前看：遇到裁定結果時，往前到開始等待的那個動作（打牌、摸牌等不標記的動作）為止，都是這次等待中的宣告。
        var pendingResolution: HistoryReplayFactDto.Reaction? = null
        for (index in transactions.indices.reversed()) {
            for (fact in transactions[index].facts.asReversed()) {
                when (fact) {
                    is HistoryReplayFactDto.Reaction -> pendingResolution = fact
                    is HistoryReplayFactDto.KnownAction -> {
                        val marker = markerOf(ruleModuleId, fact.actionType, fact.extensionTypeId)
                        val resolution = pendingResolution
                        when {
                            resolution == null || resolution.selects(fact) -> markers[index] = mostImportant(markers[index], marker)
                            marker == null && fact.actionType != HistoryActionTypeKeys.PASS -> pendingResolution = null
                        }
                    }
                    else -> Unit
                }
            }
        }
        return markers
    }

    /**
     * 判斷一次裁定結果是否選中某個宣告。
     *
     * 裁定結果沒有指出單一玩家時（例如多家同時榮和），所有宣告同一種動作的玩家都算被選中。
     *
     * @receiver 裁定結果。
     * @param declaration 等待期間的宣告。
     * @return 宣告成立時為 true。
     */
    private fun HistoryReplayFactDto.Reaction.selects(declaration: HistoryReplayFactDto.KnownAction): Boolean = actionType == declaration.actionType && (resolvedActorSeat == null || resolvedActorSeat == declaration.actorSeat)

    /**
     * 取兩個標記中較重要的一個。
     *
     * @param current 目前的標記。
     * @param candidate 新找到的標記。
     * @return 較重要的標記；兩者都沒有時為 null。
     */
    private fun mostImportant(current: GameActionReplayMarker?, candidate: GameActionReplayMarker?): GameActionReplayMarker? = listOfNotNull(current, candidate).maxOrNull()

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
