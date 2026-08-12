package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import kotlin.uuid.Uuid

/**
 * 將一次性麻將操作回饋交由目前 Minecraft 版本呈現給指定玩家。
 *
 * 實作自行決定本地化文字、顯示位置、同時觸發的視覺或聲音效果，以及版本缺少特定 API 時的降級
 * 行為。呼叫端只能提供 [MinecraftPlayerFeedback]，不得假設回饋會出現在特定 UI 元件。
 *
 * ```kotlin
 * publisher.publish(playerId, MinecraftPlayerFeedback.GameJoined)
 * ```
 */
interface MinecraftPlayerFeedbackPublisher {
    /**
     * 將 [feedback] 呈現給 [playerId]；玩家不在線時可略過。
     *
     * 各 Minecraft 版本可依自身能力決定呈現方式，建議遵守以下語意：
     *
     * - Chat 會保留在玩家可回看的聊天紀錄，適合已完成的持續狀態變更，以及重要、長篇或需要歷史
     *   紀錄的訊息。
     * - Action bar 適合短暫操作結果、條件不符及可立即重試的錯誤。
     * - Title／subtitle 適合遊戲階段轉換，或需要立即取得玩家注意力的事件。
     * - HUD 適合持續狀態，應由 snapshot／client store 驅動，不應依賴一次性 [feedback] 維持狀態。
     *
     * 同一回饋可以依版本需求觸發零到多個文字、視覺或聲音效果；實作應避免在多個位置重複顯示
     * 相同文字，除非該重複具有明確用途。
     */
    fun publish(playerId: Uuid, feedback: MinecraftPlayerFeedback)
}
