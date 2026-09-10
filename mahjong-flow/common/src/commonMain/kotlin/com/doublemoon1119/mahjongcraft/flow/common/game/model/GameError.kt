package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.flow.common.error.ApplicationError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 與對局（Game）進行中操作相關的應用層錯誤定義。
 *
 * 涵蓋所有 Game 相關 use case 中可能出現的業務邏輯錯誤情境，供 [Outcome] 的錯誤型別使用。
 *
 * 後續鳴牌/立直/胡牌/流局等 use case 視需要再擴充此 sealed interface，不需要一次列完。
 */
sealed interface GameError : ApplicationError {

    /**
     * 找不到指定的對局。
     *
     * @param gameId 欲操作的對局 Uuid。
     */
    data class GameNotFound(val gameId: Uuid) : GameError

    /**
     * 目標玩家不在指定的對局內。
     *
     * @param playerId 目標玩家 Uuid。
     * @param gameId 對局 Uuid。
     */
    data class PlayerNotInGame(val playerId: Uuid, val gameId: Uuid) : GameError

    /**
     * 玩家已耗盡思考時間，後續操作必須由伺服器自動執行。
     *
     * @param playerId 嘗試手動操作的玩家 Uuid。
     * @param gameId 對局 Uuid。
     */
    data class ForcedAutoPlayActive(val playerId: Uuid, val gameId: Uuid) : GameError

    /**
     * 尚未輪到該玩家的回合。
     *
     * @param playerId 發起操作的玩家 Uuid。
     * @param gameId 對局 Uuid。
     */
    data class NotPlayersTurn(val playerId: Uuid, val gameId: Uuid) : GameError

    /**
     * 該動作在目前的桌況下不合法。
     *
     * @param playerId 發起操作的玩家 Uuid。
     * @param gameId 對局 Uuid。
     * @param action 欲執行的動作。
     */
    data class IllegalAction(val playerId: Uuid, val gameId: Uuid, val action: GameAction) : GameError

    /**
     * 牌山已摸盡。實際的流局判定交由後續的流局 use case 處理，此處僅回報現況。
     *
     * @param gameId 對局 Uuid。
     */
    data class WallExhausted(val gameId: Uuid) : GameError

    /**
     * 對局尚未結束，不能把桌子從 Game 轉回 Room。
     *
     * @param gameId 對局 Uuid。
     */
    data class MatchNotOver(val gameId: Uuid) : GameError

    /**
     * 該對局採用的規則模組不支援目前嘗試的動作、或此動作在目前桌況下不合法，且沒有可攜帶的具體
     * [GameAction] payload（例如流局相關動作，其 payload 需要規則特有的具體流局原因型別，而
     * `:mahjong-flow` 不應該、也不需要知道那個具體型別是什麼）。
     *
     * 刻意不像 [IllegalAction] 一樣要求攜帶具體的 [GameAction]——觸發此錯誤的時間點，呼叫端
     * 尚未能建構出一個有意義的 [GameAction] 具體實例（那正是問題所在），沒有 [GameAction]
     * payload 也不影響呼叫端判斷錯誤類型。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起操作的玩家 Uuid；系統觸發（無玩家發起者，例如流局結算）的情境下為 null。
     * @param reasonId 規則或 policy 提供的完整 namespaced 原因 ID；沒有更具體原因時為 null。
     */
    data class UnsupportedAction(
        val gameId: Uuid,
        val playerId: Uuid? = null,
        val reasonId: String? = null,
    ) : GameError

    /**
     * 對局目前沒有可由指定玩家提交的開局準備步驟。
     *
     * 這通常表示該規則沒有準備階段、準備階段尚未開始、已經完成，或目前正在等待其他玩家。它描述的
     * 是權威對局狀態，不代表提交內容本身格式錯誤。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 嘗試提交準備操作的玩家 Uuid。
     */
    data class RoundPreparationUnavailable(val gameId: Uuid, val playerId: Uuid) : GameError

    /**
     * 開局準備提交不符合目前步驟要求的輸入結構或規則語意。
     *
     * 與 [RoundPreparationUnavailable] 不同，此錯誤表示玩家確實有可提交的準備步驟，但所選牌張、數量
     * 或確認資料沒有通過權威規則驗證。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 送出無效準備資料的玩家 Uuid。
     */
    data class InvalidRoundPreparationSubmission(val gameId: Uuid, val playerId: Uuid) : GameError

    /**
     * 本局缺少權威完成摘要，或規則回傳互相矛盾的 progression decision。
     *
     * [reason] 是供伺服器記錄與開發診斷使用的文字，不是穩定 ID、translation key，也不應直接顯示給
     * 玩家。
     *
     * @param gameId 對局 Uuid。
     * @param reason 描述內部狀態矛盾或例外的診斷文字。
     */
    data class InvalidMatchProgression(val gameId: Uuid, val reason: String) : GameError
}
