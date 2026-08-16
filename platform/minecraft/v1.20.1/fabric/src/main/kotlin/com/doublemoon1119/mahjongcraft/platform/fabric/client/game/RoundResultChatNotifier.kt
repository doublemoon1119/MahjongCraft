package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
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
 * 把回合結束事件（自摸／榮和／流局）連同分數變化組成一則聊天訊息。
 *
 * 這是資料流驗證用的占位呈現：`GameEventPublisher` 廣播給所有玩家的事件本身已經是結構化資料
 * （[action] + 前後 [TableStateSnapshot]），呈現方式完全是外層決定——這裡先借用聊天訊息，之後要換成
 * GUI/HUD 只需要在呼叫端換掉輸出方式，不需要動 `mahjong-flow` 或伺服端任何一行。
 *
 * 分數變化透過比較 [previousSnapshot] 與 [newSnapshot] 同一位玩家的 `score` 差異取得，而不是額外新增
 * 一個攜帶分數異動的事件型別——快照本來就已經是事件觸發後的最新結果，直接比較最單純。
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

    val previousScoresById = previousSnapshot.players.associate { it.id to it.score }
    newSnapshot.players.forEach { player ->
        val delta = player.score - (previousScoresById[player.id] ?: player.score)
        if (delta == 0) return@forEach
        message.append(Text.literal("\n")).append(
            Text.translatable(
                MinecraftMessageKeys.ROUND_RESULT_SCORE_DELTA,
                resolvePlayerDisplayName(player.id, player.isAi),
                formatScoreDelta(delta),
            ),
        )
    }
    return message
}

/**
 * 把對局結束事件（[GameAction.MatchEnded]）組成一則列出最終名次的聊天訊息，占位呈現理由同
 * [buildRoundResultChatMessage]。
 *
 * 名次單純依 [newSnapshot] 各玩家的最終 `score` 由高到低排序、依序編號——不特別處理同分的名次併列，
 * 這是占位呈現刻意的簡化，真要處理同分規則（例如依莊家順位排序）留給未來的 GUI/HUD 呈現。
 *
 * @return 不是對局結束事件時回傳 null，代表呼叫端不需要顯示任何訊息。
 */
fun buildMatchResultChatMessage(action: GameAction, newSnapshot: TableStateSnapshot): Text? {
    if (action !is GameAction.MatchEnded) return null

    val message: MutableText = Text.translatable(MinecraftMessageKeys.MATCH_RESULT_BROADCAST)
    newSnapshot.players.sortedByDescending { it.score }.forEachIndexed { index, player ->
        message.append(Text.literal("\n")).append(
            Text.translatable(
                MinecraftMessageKeys.MATCH_RESULT_RANKING_LINE,
                (index + 1).toString(),
                resolvePlayerDisplayName(player.id, player.isAi),
                player.score.toString(),
            ),
        )
    }
    return message
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

private fun formatScoreDelta(delta: Int): String = if (delta > 0) "+$delta" else delta.toString()
