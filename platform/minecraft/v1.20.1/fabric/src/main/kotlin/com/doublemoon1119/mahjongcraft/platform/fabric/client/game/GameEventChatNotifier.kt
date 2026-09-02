package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.text.buildMatchResultChatText
import com.doublemoon1119.mahjongcraft.platform.fabric.text.buildRoundResultChatText
import com.doublemoon1119.mahjongcraft.platform.fabric.text.toDisplayText
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 把回合結束事件（自摸／榮和／流局）組成一則聊天訊息，列出每位玩家「回合前 → 回合後」的名次與分數
 * 變化——這是供排行榜動畫使用的完整資料（名次升降方向、分數增減），不只是「有變化的人」。就算某位
 * 玩家自己分數沒變，也可能因為別人分數變了而被擠掉名次，所以固定列出所有玩家，不像分數變化那樣
 * 略過沒變化的人。
 *
 * 這是資料流驗證用的占位呈現：`GameEventPublisher` 廣播給所有玩家的事件本身已經是結構化資料
 * （[action] + 前後 [TableStateSnapshot]），呈現方式完全是外層決定——這裡先借用聊天訊息，之後要換成
 * GUI/HUD（例如你說的名次上下移動動畫）只需要在呼叫端換掉輸出方式，不需要動 `mahjong-flow` 或
 * 伺服端任何一行；[previousSnapshot]／[newSnapshot] 本身就已經是動畫需要的頭尾兩個關鍵影格。
 *
 * 排名（含同分決勝判準）交給 [module]（[MahjongRuleModule.compareForRoundRanking]），不在這裡寫死
 * ——跟對局結束的最終排名（[buildMatchResultChatMessage]）用的是不同的 hook
 * （[MahjongRuleModule.compareForMatchRanking]），因為這裡談的是這一局的座位，不是整場對局開始時的
 * 座位，兩者的同分決勝依據可能不同。
 *
 * @return 不是回合結束事件（自摸／榮和／流局以外的動作），或沒有前一份快照可供比較（例如玩家剛連線、
 *   還沒收到過任何 `gameUpdate`）時回傳 null，代表呼叫端不需要顯示任何訊息。
 */
fun buildRoundResultChatMessage(
    action: GameAction,
    previousSnapshot: TableStateSnapshot?,
    newSnapshot: TableStateSnapshot,
    module: MahjongRuleModule<*>,
    actionDisplayNameRegistry: GameActionDisplayNameRegistry,
    displayNameRegistry: TileDisplayNameRegistry,
    tileAssetRegistry: MinecraftTileAssetRegistry,
    tileEmojiRegistry: TileEmojiRegistry,
    exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry,
    playerDisplayName: ((Uuid, Boolean) -> String)? = null,
): Text? {
    if (action !is GameAction.Tsumo && action !is GameAction.Ron && action !is GameAction.ExhaustiveDraw) return null
    if (previousSnapshot == null) return null

    val actionText = if (action is GameAction.Ron) {
        Text.translatable(WinSettlementTranslationKeys.RON)
    } else {
        action.toDisplayText(
            referenceTile = null,
            actionDisplayNameRegistry = actionDisplayNameRegistry,
            displayNameRegistry = displayNameRegistry,
            tileAssetRegistry = tileAssetRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            exhaustiveDrawReasonDisplayNameRegistry = exhaustiveDrawReasonDisplayNameRegistry,
        )
    }
    val details: MutableText = Text.empty()

    val rankBy = module.compareForRoundRanking()
    val previousRankById = previousSnapshot.players.sortedWith(rankBy).withIndex().associate { (index, p) -> p.id to index + 1 }
    val previousScoreById = previousSnapshot.players.associate { it.id to it.score }
    val newRanked = newSnapshot.players.sortedWith(rankBy)
    val orderedAiPlayerIds = newSnapshot.players.filter { it.isAi }.map { it.id }

    newRanked.forEachIndexed { index, player ->
        val newRank = index + 1
        val previousRank = previousRankById[player.id] ?: newRank
        val previousScore = previousScoreById[player.id] ?: player.score
        if (index > 0) details.append(Text.literal("\n"))
        details.append(
            Text.translatable(
                MinecraftMessageKeys.ROUND_RESULT_PLAYER_LINE,
                playerDisplayName?.invoke(player.id, player.isAi)
                    ?: resolvePlayerDisplayName(player.id, player.isAi, orderedAiPlayerIds),
                previousRank.toString(),
                newRank.toString(),
                rankChangeSymbol(previousRank, newRank),
                previousScore.toString(),
                player.score.toString(),
            ),
        )
    }

    return buildRoundResultChatText(actionText, details)
}

/** `↑`：名次數字變小（進步）；`↓`：名次數字變大（退步）；`→`：名次沒變。 */
private fun rankChangeSymbol(previousRank: Int, newRank: Int): String = when {
    newRank < previousRank -> "↑"
    newRank > previousRank -> "↓"
    else -> "→"
}

/**
 * 把對局結束事件（[GameAction.MatchEnded]）組成一則列出最終名次的聊天訊息，占位呈現理由同
 * [buildRoundResultChatMessage]。
 *
 * 排名（含同分決勝判準）交給 [module]（[MahjongRuleModule.compareForMatchRanking]），不在這裡寫死。
 *
 * @return 不是對局結束事件時回傳 null，代表呼叫端不需要顯示任何訊息。
 */
fun buildMatchResultChatMessage(
    action: GameAction,
    newSnapshot: TableStateSnapshot,
    module: MahjongRuleModule<*>,
    playerDisplayName: ((Uuid, Boolean) -> String)? = null,
): Text? {
    if (action !is GameAction.MatchEnded) return null

    val details = Text.empty()
    appendRankingLines(details, newSnapshot.players.sortedWith(module.compareForMatchRanking()), playerDisplayName)
    return buildMatchResultChatText(details)
}

/** 把 [rankedPlayers]（已排好序）依序編號附加到 [message]，兩種排名訊息共用同一種每行格式。 */
private fun appendRankingLines(
    message: MutableText,
    rankedPlayers: List<MahjongPlayerSnapshot>,
    playerDisplayName: ((Uuid, Boolean) -> String)?,
) {
    val orderedAiPlayerIds = rankedPlayers.filter { it.isAi }.sortedBy { it.initialSeatIndex }.map { it.id }
    rankedPlayers.forEachIndexed { index, player ->
        if (index > 0) message.append(Text.literal("\n"))
        message.append(
            Text.translatable(
                MinecraftMessageKeys.RANKING_LINE,
                (index + 1).toString(),
                playerDisplayName?.invoke(player.id, player.isAi)
                    ?: resolvePlayerDisplayName(player.id, player.isAi, orderedAiPlayerIds),
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
    if (isAi) return com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName(id, orderedAiPlayerIds)
    val name = MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(id.toJavaUuid())?.profile?.name
    return name ?: id.toString().take(8)
}
