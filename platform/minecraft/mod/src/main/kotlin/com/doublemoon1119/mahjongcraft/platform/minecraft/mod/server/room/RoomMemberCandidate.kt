package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room

import kotlin.uuid.Uuid

/**
 * `/mahjongcraft room kick`／`ai strategy` 等指令共用的房間成員候選目標。
 *
 * @property playerId 實際指向的成員 Uuid。
 * @property token 打進聊天輸入框的實際文字：
 *   - AI 用與語言無關的代號。Brigadier 補全文字只能是純字串，不能是可翻譯的 [net.minecraft.network.chat.Component]，
 *     同一個指令也不應因輸入者的語言而不同。
 *   - 真人玩家直接用 Minecraft 使用者名稱，本來就不用翻譯。
 * @property aiSequence 這個候選是 AI 時，它在目前房間內的顯示序號，真人玩家為 `null`。呼叫端用這個序號組出
 *   `Component.translatable` tooltip，翻譯交給客戶端依玩家語言處理。
 * @property strategyKey 這個候選是 AI 時，它登記使用的 AI 策略 key，真人玩家為 `null`。
 */
data class RoomMemberCandidate(
    val playerId: Uuid,
    val token: String,
    val aiSequence: Int? = null,
    val strategyKey: String? = null,
)
