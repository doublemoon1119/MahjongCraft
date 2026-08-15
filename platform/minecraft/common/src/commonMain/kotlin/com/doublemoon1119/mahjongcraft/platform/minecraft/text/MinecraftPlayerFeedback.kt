package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation

/**
 * Minecraft 玩家完成麻將操作後收到的一次性回饋語意。
 *
 * 此模型只描述發生的結果與呈現該結果所需的資料，不攜帶 translation key、重要程度或 chat、title、
 * HUD 等顯示位置。這些資訊會限制不同 Minecraft 版本能採用的呈現能力，因此由各版本 adapter 自行
 * 映射。例如 1.20.1 可以將 [GameAlreadyStarted] 顯示於 chat，未來版本則可針對同一回饋同時顯示新版
 * bar 並播放音效，而 Room／Game 呼叫端不需要改動：
 *
 * ```kotlin
 * feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameAlreadyStarted)
 *
 * // 1.20.1 adapter
 * GameAlreadyStarted -> player.sendMessage(Text.translatable(GAME_ALREADY_STARTED_KEY), true)
 *
 * // future-version adapter
 * GameAlreadyStarted -> {
 *     player.showNewBar(localizedText)
 *     player.playConfirmationSound()
 * }
 * ```
 *
 * 持續存在的 HUD 狀態應由 snapshot／client store 驅動；需要玩家回答的動作詢問應使用具備識別碼、
 * 合法動作與逾時資訊的 request／response 協議，兩者都不屬於此一次性回饋模型。
 */
sealed interface MinecraftPlayerFeedback {
    /** 對局已開始，玩家無法中途加入。 */
    data object GameAlreadyStarted : MinecraftPlayerFeedback

    /**
     * 已建立麻將遊戲。
     *
     * @property location 麻將桌所在位置；理論上一定查得到，查不到時（例如位置索引與方塊實體出現
     *   競態）呈現端會退回不含位置的純文字訊息，不影響「已建立」這件事本身的通知。
     */
    data class GameCreated(val location: TableLocation?) : MinecraftPlayerFeedback

    /** 已加入麻將遊戲。 */
    data object GameJoined : MinecraftPlayerFeedback

    /** 玩家不在指定麻將遊戲中。 */
    data object PlayerNotInGame : MinecraftPlayerFeedback

    /** 對局進行中禁止主動離開。 */
    data object GameLeaveDeniedWhilePlaying : MinecraftPlayerFeedback

    /** 房主已解散麻將遊戲。 */
    data object GameDissolved : MinecraftPlayerFeedback

    /** 玩家已離開麻將遊戲。 */
    data object GameLeft : MinecraftPlayerFeedback

    /** 離開麻將遊戲失敗。 */
    data object GameLeaveFailed : MinecraftPlayerFeedback

    /** 玩家已參與另一場麻將遊戲。 */
    data object PlayerAlreadyInGame : MinecraftPlayerFeedback

    /** 加入麻將遊戲失敗。 */
    data object GameJoinFailed : MinecraftPlayerFeedback

    /**
     * 已切換準備狀態。
     *
     * @property isReady 切換後是否為已準備。
     */
    data class ReadyToggled(val isReady: Boolean) : MinecraftPlayerFeedback

    /** 遊戲主持人不參與準備機制，開局請直接使用開始對局的操作。 */
    data object HostReadyNotRequired : MinecraftPlayerFeedback

    /** 只有遊戲主持人可以開始對局。 */
    data object NotGameHost : MinecraftPlayerFeedback

    /** 目前人數不符合規則限制的人數區間，無法開始對局。 */
    data object InvalidPlayerCount : MinecraftPlayerFeedback

    /** 還有玩家尚未準備好，無法開始對局。 */
    data object NotAllPlayersReady : MinecraftPlayerFeedback

    /** 開始遊戲失敗。 */
    data object GameStartFailed : MinecraftPlayerFeedback

    /** 指定的麻將桌不存在，或已超出目前可互動的範圍。 */
    data object TableNotReachable : MinecraftPlayerFeedback

    /**
     * 已新增 AI 玩家。
     *
     * @property strategyKey 該 AI 實際使用的策略登記 key，供呈現端顯示策略名稱。
     */
    data class AiAdded(val strategyKey: String) : MinecraftPlayerFeedback

    /** 新增 AI 玩家失敗。 */
    data object AddAiFailed : MinecraftPlayerFeedback

    /** 遊戲人數已滿，無法再新增 AI 玩家。 */
    data object GameFull : MinecraftPlayerFeedback

    /** 已將指定玩家移出遊戲（房主視角）。 */
    data object PlayerKicked : MinecraftPlayerFeedback

    /** 已被遊戲主持人移出遊戲（被踢玩家視角）。 */
    data object KickedFromGame : MinecraftPlayerFeedback

    /** 房主不能將自己移出遊戲。 */
    data object CannotKickSelf : MinecraftPlayerFeedback

    /** 將玩家移出遊戲失敗。 */
    data object KickFailed : MinecraftPlayerFeedback

    /**
     * 已更換 AI 策略。
     *
     * @property aiSequence 該 AI 目前在房間內的顯示序號，供呈現端指出換的是哪一個 AI。
     * @property oldStrategyKey 更換前的策略登記 key。
     * @property newStrategyKey 更換後的策略登記 key。
     */
    data class AiStrategyChanged(
        val aiSequence: Int,
        val oldStrategyKey: String,
        val newStrategyKey: String,
    ) : MinecraftPlayerFeedback

    /** 目標玩家不是 AI，無法更換策略。 */
    data object TargetNotAi : MinecraftPlayerFeedback

    /** 更換 AI 策略失敗。 */
    data object ChangeAiStrategyFailed : MinecraftPlayerFeedback
}
