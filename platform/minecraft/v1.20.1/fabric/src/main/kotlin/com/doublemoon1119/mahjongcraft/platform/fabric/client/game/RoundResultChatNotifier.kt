package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.text.toDisplayText
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
 * 名次同分時依 `currentWind`（這一局的座位方位，隨連莊/過莊輪轉）決定順序，越靠近這一局的東家名次
 * 越前面；跟對局結束的最終排名（[buildMatchResultChatMessage]）改用 `initialSeat`（起家）不同，
 * 因為這裡談的是這一局的座位，不是整場對局開始時的座位。
 *
 * @return 不是回合結束事件（自摸／榮和／流局以外的動作），或沒有前一份快照可供比較（例如玩家剛連線、
 *   還沒收到過任何 `gameUpdate`）時回傳 null，代表呼叫端不需要顯示任何訊息。
 */
fun buildRoundResultChatMessage(
    action: GameAction,
    previousSnapshot: TableStateSnapshot?,
    newSnapshot: TableStateSnapshot,
    displayNameRegistry: TileDisplayNameRegistry,
    tileAssetRegistry: MinecraftTileAssetRegistry,
    tileEmojiRegistry: TileEmojiRegistry,
): Text? {
    if (action !is GameAction.Tsumo && action !is GameAction.Ron && action !is GameAction.ExhaustiveDraw) return null
    if (previousSnapshot == null) return null

    val actionText = action.toDisplayText(
        referenceTile = null,
        displayNameRegistry = displayNameRegistry,
        tileAssetRegistry = tileAssetRegistry,
        tileEmojiRegistry = tileEmojiRegistry,
    )
    val message: MutableText = Text.translatable(MinecraftMessageKeys.ROUND_RESULT_BROADCAST, actionText)

    val rankBy = compareByDescending<MahjongPlayerSnapshot> { it.score }.thenBy { it.currentWind.ordinal }
    val previousRankById = previousSnapshot.players.sortedWith(rankBy).withIndex().associate { (index, p) -> p.id to index + 1 }
    val previousScoreById = previousSnapshot.players.associate { it.id to it.score }
    val newRanked = newSnapshot.players.sortedWith(rankBy)

    newRanked.forEachIndexed { index, player ->
        val newRank = index + 1
        val previousRank = previousRankById[player.id] ?: newRank
        val previousScore = previousScoreById[player.id] ?: player.score
        message.append(Text.literal("\n")).append(
            Text.translatable(
                MinecraftMessageKeys.ROUND_RESULT_PLAYER_LINE,
                resolvePlayerDisplayName(player.id, player.isAi),
                previousRank.toString(),
                newRank.toString(),
                rankChangeSymbol(previousRank, newRank),
                previousScore.toString(),
                player.score.toString(),
            ),
        )
    }

    return message
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
 * 名次依 [newSnapshot] 各玩家的最終 `score` 由高到低排序；同分時依 `initialSeat` 決定順序——
 * `initialSeat` 是開局當下（起家／初始東家）分配的座位，終局前都不會再變（會變的是 `currentWind`，
 * 隨連莊/過莊輪轉），座位越靠近起家（東 → 南 → 西 → 北）名次越前面，這是日本麻將常見的同分決勝
 * 慣例。
 *
 * @return 不是對局結束事件時回傳 null，代表呼叫端不需要顯示任何訊息。
 */
fun buildMatchResultChatMessage(action: GameAction, newSnapshot: TableStateSnapshot): Text? {
    if (action !is GameAction.MatchEnded) return null

    val message: MutableText = Text.translatable(MinecraftMessageKeys.MATCH_RESULT_BROADCAST)
    appendRankingLines(message, newSnapshot.players.sortedWith(compareByDescending<MahjongPlayerSnapshot> { it.score }.thenBy { it.initialSeat.ordinal }))
    return message
}

/** 把 [rankedPlayers]（已排好序）依序編號附加到 [message]，兩種排名訊息共用同一種每行格式。 */
private fun appendRankingLines(message: MutableText, rankedPlayers: List<MahjongPlayerSnapshot>) {
    rankedPlayers.forEachIndexed { index, player ->
        message.append(Text.literal("\n")).append(
            Text.translatable(
                MinecraftMessageKeys.RANKING_LINE,
                (index + 1).toString(),
                resolvePlayerDisplayName(player.id, player.isAi),
                player.score.toString(),
            ),
        )
    }
}

/**
 * 真人玩家的麻將 Uuid 就是其 Minecraft 帳號 Uuid（見 `MahjongTableRoomService.join`），可以直接查
 * 玩家清單解析出真實 ID；查不到（不在同一伺服器可見範圍）或是 AI 玩家則退回顯示短 ID 佔位。
 */
private fun resolvePlayerDisplayName(id: Uuid, isAi: Boolean): String {
    if (isAi) return "AI-" + id.toString().take(4)
    val name = MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(id.toJavaUuid())?.profile?.name
    return name ?: id.toString().take(8)
}
