package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.text.ClickEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text

/**
 * 建立正式的單行 round-result 訊息，將 [details] 收進中括號互動標籤的 hover 內容。正式事件與開發期
 * 測試指令共用這個 builder，避免提示格式分歧。
 *
 * @param actionText 結算結果的顯示文字，例如自摸或流局原因。
 * @param details 每位玩家結算前後的名次與分數。
 * @param roundContinues 本局在這次和牌後仍繼續時為 true，標題改為和牌而不是本局結束。
 * @return 使用既有聊天格式的結算訊息。
 */
fun buildRoundResultChatText(
    actionText: Text,
    details: Text,
    roundContinues: Boolean,
): MutableText = Text
    .translatable(
        if (roundContinues) MinecraftMessageKeys.CONTINUING_WIN_RESULT_BROADCAST else MinecraftMessageKeys.ROUND_RESULT_BROADCAST,
        actionText,
    )
    .append(Text.literal(" "))
    .append(bracketedInteractiveLabel(Text.translatable(MinecraftMessageKeys.ROUND_RESULT_DETAILS_LABEL), details))

/**
 * 建立單行對局結果訊息，排行與選用歷史入口集中於同一標籤。
 *
 * @param details 完整排行與入口說明。
 * @param historyClick 選用的本地歷史開啟命令。
 * @return 使用既有聊天格式的結果訊息。
 */
fun buildMatchResultChatText(details: Text, historyClick: ClickEvent? = null): MutableText = Text
    .translatable(MinecraftMessageKeys.MATCH_RESULT_BROADCAST)
    .append(Text.literal(" "))
    .append(bracketedInteractiveLabel(Text.translatable(MinecraftMessageKeys.ROUND_RESULT_DETAILS_LABEL), details, historyClick))
