package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import kotlin.uuid.Uuid

/**
 * `/mahjongcraft room kick`／`ai strategy` 等指令共用的房間成員候選目標。
 *
 * @property playerId 實際指向的成員 Uuid。
 * @property token 打進聊天輸入框的實際文字。伺服器端在 1.20.1 無法得知玩家客戶端語言，Brigadier
 *   補全文字本身也只能是純字串，不能是可依語言翻譯的 [net.minecraft.text.Text]，因此固定用語言無關
 *   的技術性代號（真人玩家則直接用其 Minecraft 使用者名稱，本來就無需翻譯）。
 *
 * @property aiSequence 這個候選是 AI 時，牠在目前房間內的顯示序號；真人玩家為 `null`。呼叫端用這個
 *   序號組出 `Text.translatable` tooltip，翻譯交給客戶端依玩家語言處理。
 *
 * @property strategyKey 這個候選是 AI 時，牠登記使用的 AI 策略 key；真人玩家為 `null`。
 */
data class RoomMemberCandidate(
    val playerId: Uuid,
    val token: String,
    val aiSequence: Int? = null,
    val strategyKey: String? = null,
)
