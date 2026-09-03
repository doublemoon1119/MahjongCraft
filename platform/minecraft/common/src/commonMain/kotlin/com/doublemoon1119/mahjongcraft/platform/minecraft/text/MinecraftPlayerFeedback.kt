package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
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
 *
 * 房間等待室 GUI（`RoomScreen`）與對局操作 HUD 都已完成，原本為了填補它們尚未存在而加上的自動
 * chat 通知（輪到自己、可執行動作、決策倒數）已全部移除。保留下來的回饋都是刻意的一次性訊息：
 *   - 指令明確要求的輸出，例如 [ShowHand]／[ShowGameConfig]。
 *   - 操作被拒絕的原因，例如 [NotYourTurn]／[IllegalGameAction]／[GameJoinFailed]／[KickFailed]。
 *   - 遊戲建立、加入、離開與設定變更的結果，例如 [GameCreated]／[ReadyToggled]／
 *     [AiStrategyChanged]／[GameConfigChanged]。
 *
 * 捨牌、摸牌這類高頻操作的回饋走 action bar 而非聊天欄，避免洗版；呈現方式由各 Minecraft 版本
 * adapter 自行決定，回饋本身不攜帶 translation key 或顯示 channel。
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

    /** 玩家目前不屬於任何麻將遊戲。 */
    data object PlayerNotInAnyGame : MinecraftPlayerFeedback

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

    /**
     * 已變更遊戲設定。呈現端可以將 [oldConfigJson]／[newConfigJson] 解析、轉換成 hover 顯示 TOML、
     * 點擊複製 JSON 的可互動文字；攜帶已序列化字串而非設定領域物件，是因為序列化需要的
     * `NetworkDtoRegistries`／`Json` 屬於 network-dto 層，這個模組不依賴該層。
     *
     * @property oldConfigJson 變更前設定的 JSON 序列化文字。
     * @property newConfigJson 變更後設定的 JSON 序列化文字。
     */
    data class GameConfigChanged(val oldConfigJson: String, val newConfigJson: String) : MinecraftPlayerFeedback

    /**
     * 提供的新設定與目前設定相同，操作本身仍算成功（冪等），呈現端不應顯示成「舊設定 → 新設定」。
     *
     * @property configJson 目前設定的 JSON 序列化文字。
     */
    data class GameConfigUnchanged(val configJson: String) : MinecraftPlayerFeedback

    /** 變更遊戲設定失敗。 */
    data object ChangeGameConfigFailed : MinecraftPlayerFeedback

    /**
     * 顯示所在麻將遊戲目前的設定；呈現端可將 [configJson] 組成可 hover 顯示 TOML、點擊開啟設定編輯
     * 畫面的可互動文字。
     *
     * @property configJson 目前設定的 JSON 序列化文字。
     */
    data class ShowGameConfig(val configJson: String) : MinecraftPlayerFeedback

    /** 還沒輪到該玩家的回合。 */
    data object NotYourTurn : MinecraftPlayerFeedback

    /** 玩家已逾時，後續操作交由伺服器自動處理。 */
    data object ForcedAutoPlayActive : MinecraftPlayerFeedback

    /** 該動作在目前桌況下不合法。 */
    data object IllegalGameAction : MinecraftPlayerFeedback

    /** 牌山已摸盡。 */
    data object WallExhausted : MinecraftPlayerFeedback

    /** 目前規則不支援這個動作。 */
    data object UnsupportedGameAction : MinecraftPlayerFeedback

    /** 桌面正在播放呈現動畫（例如擲骰），暫時無法送出操作，請稍候再試。 */
    data object TableAnimationBusy : MinecraftPlayerFeedback

    /**
     * 顯示玩家目前的手牌、副露與可執行的特殊動作，供 `/mahjongcraft game hand` 使用。
     *
     * @property standingTiles 立牌（含剛摸到的牌）。
     * @property melds 副露列表。
     * @property turnStatus 目前是否輪到自己／有資格回應／純粹等待，決定 [legalActions] 為空時要顯示
     *   哪一種提示（例如區分「輪到你但沒有特殊動作」與「還沒輪到你」，避免誤導玩家以為隨時都能
     *   `discard`）。
     * @property legalActions 目前可執行的特殊動作清單（不含永遠合法的捨牌），
     *   與 [LegalActionValidator] 既有的「空清單不代表
     *   不能捨牌」慣例一致；每個項目額外帶上該動作涉及的牌面（可能為 null），且與
     *   `/mahjongcraft game action` 指令 Tab 補全候選項目使用同一份查詢結果、同一個順序。
     */
    data class ShowHand(
        val standingTiles: List<Tile>,
        val melds: List<Meld>,
        val turnStatus: GameTurnStatus,
        val legalActions: List<Pair<GameAction, Tile?>>,
    ) : MinecraftPlayerFeedback
}
