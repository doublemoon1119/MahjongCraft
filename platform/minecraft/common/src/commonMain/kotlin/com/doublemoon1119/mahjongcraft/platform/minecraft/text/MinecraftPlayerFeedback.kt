package com.doublemoon1119.mahjongcraft.platform.minecraft.text

/**
 * Minecraft 玩家完成麻將操作後收到的一次性回饋語意。
 *
 * 此模型只描述發生的結果與呈現該結果所需的資料，不攜帶 translation key、重要程度或 chat、title、
 * HUD 等顯示位置。這些資訊會限制不同 Minecraft 版本能採用的呈現能力，因此由各版本 adapter 自行
 * 映射。例如 1.20.1 可以將 [GameCreated] 顯示於 chat，未來版本則可針對同一回饋同時顯示新版 bar
 * 並播放音效，而 Room／Game 呼叫端不需要改動：
 *
 * ```kotlin
 * feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.GameCreated)
 *
 * // 1.20.1 adapter
 * GameCreated -> player.sendMessage(Text.translatable(GAME_CREATED_KEY))
 *
 * // future-version adapter
 * GameCreated -> {
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

    /** 已建立麻將遊戲。 */
    data object GameCreated : MinecraftPlayerFeedback

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

    /** 已切換準備狀態。 */
    data object ReadyToggled : MinecraftPlayerFeedback

    /** 只有遊戲主持人可以開始對局。 */
    data object NotGameHost : MinecraftPlayerFeedback

    /** 還有玩家尚未準備好，無法開始對局。 */
    data object NotAllPlayersReady : MinecraftPlayerFeedback

    /** 開始遊戲失敗。 */
    data object GameStartFailed : MinecraftPlayerFeedback

    /** 指定的麻將桌不存在，或已超出目前可互動的範圍。 */
    data object TableNotReachable : MinecraftPlayerFeedback
}
