package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.text.MutableText
import net.minecraft.text.Text

/**
 * 建立正式的單行 round-result 訊息，將 [details] 收進中括號互動標籤的 hover 內容。正式事件與開發期
 * 測試指令共用這個 builder，避免提示格式分歧。
 */
fun buildRoundResultChatText(actionText: Text, details: Text): MutableText = Text
    .translatable(MinecraftMessageKeys.ROUND_RESULT_BROADCAST, actionText)
    .append(Text.literal(" "))
    .append(bracketedInteractiveLabel(Text.translatable(MinecraftMessageKeys.ROUND_RESULT_DETAILS_LABEL), details))
