package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.text.buildMatchResultChatText
import com.doublemoon1119.mahjongcraft.platform.fabric.text.buildRoundResultChatText
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundResultKindDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundResultPayloadDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementTextKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.client.MinecraftClient
import net.minecraft.text.ClickEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 把伺服器送來的結算結果（和牌或流局）組成一則聊天訊息，依結算後名次列出每位玩家結算前後的名次與分數。
 *
 * 自己分數沒變的玩家也可能因為別人分數改變而名次升降，因此固定列出所有玩家。前後分數與名次原樣使用 [result] 的值，
 * 不比對 client 保存的快照。本局在這次和牌後仍繼續時，標題為和牌而不是本局結束。
 *
 * @param result 伺服器送來的結算結果。
 * @param actionVocabularyRegistry 依規則查詢自摸、流局宣告等動作用語。
 * @param roundOutcomeDisplayNameRegistry 規則特殊結果（例如日麻的流局滿貫）的顯示名稱。
 * @param exhaustiveDrawReasonDisplayNameRegistry 流局原因的顯示名稱。
 * @param playerDisplayName 選用的玩家名稱解析。
 * @return 結算結果的聊天訊息。
 */
fun buildRoundResultChatMessage(
    result: RoundResultPayloadDto,
    actionVocabularyRegistry: GameActionVocabularyRegistry,
    roundOutcomeDisplayNameRegistry: RoundOutcomeDisplayNameRegistry,
    exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry,
    playerDisplayName: ((Uuid, Boolean) -> String)? = null,
): Text {
    val orderedAiPlayerIds = result.players.filter { it.isAi }.sortedBy { it.seatIndex }.map { Uuid.parse(it.playerId) }
    val details: MutableText = Text.empty()
    result.players.sortedBy { it.currentRank }.forEachIndexed { index, player ->
        val playerId = Uuid.parse(player.playerId)
        if (index > 0) details.append(Text.literal("\n"))
        details.append(
            Text.translatable(
                MinecraftMessageKeys.ROUND_RESULT_PLAYER_LINE,
                playerDisplayName?.invoke(playerId, player.isAi)
                    ?: resolvePlayerDisplayName(playerId, player.isAi, orderedAiPlayerIds),
                player.previousRank.toString(),
                player.currentRank.toString(),
                rankChangeSymbol(player.previousRank, player.currentRank),
                player.previousScore.toString(),
                player.currentScore.toString(),
            ),
        )
    }
    val outcomeText = roundOutcomeText(
        result = result,
        actionVocabularyRegistry = actionVocabularyRegistry,
        roundOutcomeDisplayNameRegistry = roundOutcomeDisplayNameRegistry,
        exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
    )
    return buildRoundResultChatText(outcomeText, details, result.roundContinues)
}

/**
 * 結算結果的顯示文字：自摸使用規則的動作用語，榮和使用結算用語；其他結果依序查規則的動作用語（例如九種九牌）、
 * 特殊結果名稱與流局原因名稱，都沒有登記時流局退回通用流局文字、和牌顯示結果 ID。
 */
private fun roundOutcomeText(
    result: RoundResultPayloadDto,
    actionVocabularyRegistry: GameActionVocabularyRegistry,
    roundOutcomeDisplayNameRegistry: RoundOutcomeDisplayNameRegistry,
    exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry,
): Text = when (result.outcomeId) {
    BuiltInRoundOutcomeIds.TSUMO -> Text.translatable(
        actionVocabularyRegistry.find(result.ruleModuleId, BuiltInGameActionIds.TSUMO)?.messageKey ?: WinSettlementTextKeys.TSUMO,
    )
    BuiltInRoundOutcomeIds.RON -> Text.translatable(WinSettlementTextKeys.RON)
    else -> (
        actionVocabularyRegistry.find(result.ruleModuleId, result.outcomeId)?.messageKey
            ?: roundOutcomeDisplayNameRegistry.find(result.outcomeId)
            ?: exhaustiveDrawReasonDisplayNameRegistry.find(result.outcomeId)
        )?.let(Text::translatable)
        ?: when (result.kind) {
            RoundResultKindDto.DRAW -> Text.translatable(MinecraftMessageKeys.GAME_ACTION_EXHAUSTIVE_DRAW)
            RoundResultKindDto.WIN -> Text.literal(result.outcomeId)
        }
}

/** `↑`：名次數字變小（進步）；`↓`：名次數字變大（退步）；`→`：名次沒變。 */
private fun rankChangeSymbol(previousRank: Int, newRank: Int): String = when {
    newRank < previousRank -> "↑"
    newRank > previousRank -> "↓"
    else -> "→"
}

/**
 * 把對局結束事件（[GameAction.MatchEnded]）組成一則列出最終名次的聊天訊息。
 *
 * 排名（含同分決勝判準）交給 [module]（[MahjongRuleModule.compareForMatchRanking]），不在這裡寫死。
 *
 * @param action 欲呈現的對局事件。
 * @param newSnapshot 結束對局的可見桌況。
 * @param aiPlayerIds 由 AI 操控的玩家。
 * @param module 提供權威排名排序的規則模組。
 * @param playerDisplayName 選用的玩家名稱解析。
 * @param historyCommand 選用的本地歷史畫面開啟命令，不以保存完成為前提。
 * @return 不是對局結束事件時回傳 null，代表呼叫端不需要顯示任何訊息。
 */
fun buildMatchResultChatMessage(
    action: GameAction,
    newSnapshot: TableStateSnapshot,
    aiPlayerIds: Set<Uuid>,
    module: MahjongRuleModule<*>,
    historyCommand: String? = null,
    playerDisplayName: ((Uuid, Boolean) -> String)? = null,
): Text? {
    if (action !is GameAction.MatchEnded) return null

    val details = Text.empty()
    appendRankingLines(details, newSnapshot.players.sortedWith(module.compareForMatchRanking()), aiPlayerIds, playerDisplayName)
    if (historyCommand != null) details.append(Text.literal("\n").append(Text.translatable(HISTORY_OPEN_HINT_KEY).formatted(Formatting.AQUA)))
    return buildMatchResultChatText(details, historyCommand?.let { ClickEvent(ClickEvent.Action.RUN_COMMAND, it) })
}

/** 對局歷史畫面入口說明的翻譯鍵。 */
private const val HISTORY_OPEN_HINT_KEY = MinecraftHistoryScreenKeys.OPEN_HINT

/** 把 [rankedPlayers]（已排好序）依序編號附加到 [message]，兩種排名訊息共用同一種每行格式。 */
private fun appendRankingLines(
    message: MutableText,
    rankedPlayers: List<MahjongPlayerSnapshot>,
    aiPlayerIds: Set<Uuid>,
    playerDisplayName: ((Uuid, Boolean) -> String)?,
) {
    val orderedAiPlayerIds = rankedPlayers.filter { it.id in aiPlayerIds }.sortedBy { it.initialSeatIndex }.map { it.id }
    rankedPlayers.forEachIndexed { index, player ->
        if (index > 0) message.append(Text.literal("\n"))
        message.append(
            Text.translatable(
                MinecraftMessageKeys.RANKING_LINE,
                (index + 1).toString(),
                playerDisplayName?.invoke(player.id, player.id in aiPlayerIds)
                    ?: resolvePlayerDisplayName(player.id, player.id in aiPlayerIds, orderedAiPlayerIds),
                player.score.toString(),
            ),
        )
    }
}

/**
 * 真人玩家的麻將 Uuid 就是其 Minecraft 帳號 Uuid（見 `MahjongTableRoomService.join`），可以直接查
 * 玩家清單解析出真實 ID；查不到（不在同一伺服器可見範圍）或是 AI 玩家則退回顯示短 ID 佔位。
 */
private fun resolvePlayerDisplayName(id: Uuid, isAi: Boolean, orderedAiPlayerIds: List<Uuid>): String {
    if (isAi) return aiPlayerDisplayName(id, orderedAiPlayerIds)
    val name = MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(id.toJavaUuid())?.profile?.name
    return name ?: id.toString().take(8)
}
