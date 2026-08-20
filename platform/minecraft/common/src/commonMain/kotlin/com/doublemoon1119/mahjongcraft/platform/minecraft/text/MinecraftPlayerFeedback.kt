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
 * TODO: 目前 1.20.1 Fabric adapter 一律轉成 chat 訊息，是等對應 GUI／HUD 做出來之前的暫時方案，
 *   不是最終呈現方式；屆時這裡的回饋語意不需要更動，只需要改 adapter 端怎麼呈現：
 *   - 房間階段的回饋（[GameCreated]／[ReadyToggled]／[AiAdded]／[PlayerKicked]／
 *     [AiStrategyChanged]／[ShowGameConfig]／[GameConfigChanged] 等）將由「房間等待室 GUI」取代。
 *   - 對局階段的回饋（[GameActionPerformed]／[ShowHand]／[YourTurn]／[NotYourTurn] 等）將由
 *     「遊戲桌面 GUI」與思考時間 HUD 取代／補強。
 *   單純的操作失敗提示（例如 [GameJoinFailed]／[KickFailed]）預期即使有了 GUI 仍會保留 chat 或類似
 *   的一次性錯誤提示，不在此列。
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

    /** 提供的 JSON 無法解析成合法的遊戲設定。 */
    data object InvalidGameConfig : MinecraftPlayerFeedback

    /** 變更遊戲設定失敗。 */
    data object ChangeGameConfigFailed : MinecraftPlayerFeedback

    /**
     * 顯示所在麻將遊戲目前的設定；呈現端可將 [configJson] 組成可 hover 顯示 TOML、點擊開啟設定編輯
     * 畫面的可互動文字。
     *
     * @property configJson 目前設定的 JSON 序列化文字。
     */
    data class ShowGameConfig(val configJson: String) : MinecraftPlayerFeedback

    /**
     * 已成功執行一次對局操作。
     *
     * @property action 實際執行的動作。
     * @property referenceTile 該動作涉及的牌面（例如捨牌／吃／碰／槓/榮和的目標牌），呈現端組訊息用；
     *   [GameAction.Tsumo]／[GameAction.Pass]／[GameAction.ExhaustiveDraw] 等不涉及特定牌面的動作
     *   為 null。
     */
    data class GameActionPerformed(val action: GameAction, val referenceTile: Tile?) : MinecraftPlayerFeedback

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

    /**
     * 輪到自己回合，伺服器已代為摸牌完成。真人玩家的摸牌是全自動觸發（見
     * `MahjongAutoDrawService`），沒有這則主動通知的話玩家完全不會知道輪到自己了，只能自己反覆查詢
     * `/mahjongcraft game hand`。
     *
     * TODO: 目前刻意只做一次性 chat 訊息，不是持續顯示的倒數計時；之後只對目前決策玩家顯示的思考
     *   時間 HUD 會取代／補強這裡，現在不需要為了倒數顯示另外設計機制。
     *
     * @property drawnTile 這次自動摸到的牌。
     */
    data class YourTurn(val drawnTile: Tile) : MinecraftPlayerFeedback
}
