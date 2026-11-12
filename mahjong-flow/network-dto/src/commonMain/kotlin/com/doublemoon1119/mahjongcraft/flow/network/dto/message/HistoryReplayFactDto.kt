package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RuleDecisionTimingDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Replay 語意事實 DTO；各子類別只傳輸公開欄位。
 *
 * @property typeKey 穩定事實種類識別碼。
 */
@Serializable
sealed interface HistoryReplayFactDto {
    val typeKey: String

    /**
     * 已知動作的安全事實。
     *
     * @property typeKey 事實種類識別碼。
     * @property actorSeat 發起者座位。
     * @property actionType 動作種類。
     * @property directTiles 直接涉及的牌索引。
     * @property revealedTiles 新公開的牌索引。
     * @property extensionTypeId 擴充動作種類。
     */
    @SerialName("known_action")
    @Serializable
    data class KnownAction(
        override val typeKey: String,
        val actorSeat: Int?,
        val actionType: String,
        val directTiles: List<Int>,
        val revealedTiles: List<Int>,
        val extensionTypeId: String?,
    ) : HistoryReplayFactDto

    /**
     * 動作即時收付的安全事實。
     *
     * @property typeKey 事實種類識別碼。
     * @property actorSeat 執行動作的玩家座位。
     * @property actionType 觸發收付的動作種類。
     * @property extensionTypeId 擴充動作種類。
     * @property scoreDeltasBySeat 各座位的點數增減。
     * @property potDeposit 放進規則點數池的點數，正數為放入、負數為取出。
     */
    @SerialName("action_scored")
    @Serializable
    data class ActionScored(
        override val typeKey: String,
        val actorSeat: Int?,
        val actionType: String,
        val extensionTypeId: String?,
        val scoreDeltasBySeat: Map<Int, Int>,
        val potDeposit: Int,
    ) : HistoryReplayFactDto

    /**
     * 已裁定回應的安全事實。
     *
     * @property typeKey 事實種類識別碼。
     * @property actionType 動作種類。
     * @property resolvedActorSeat 裁定玩家座位。
     */
    @SerialName("reaction")
    @Serializable
    data class Reaction(
        override val typeKey: String,
        val actionType: String?,
        val resolvedActorSeat: Int?,
    ) : HistoryReplayFactDto

    /**
     * 局選擇步驟的安全事實。
     *
     * @property typeKey 事實種類識別碼。
     * @property timing 選擇視窗開啟的時間點。
     * @property stepId 步驟識別碼。
     * @property stepIndex 步驟索引。
     * @property nextStepId 下一步識別碼。
     * @property actorSeat 提交選擇的玩家座位；不是玩家提交的事實為 null。
     * @property optionId 玩家選擇的選項識別碼；不是單選提交時為 null。
     * @property passed 玩家選的是否為代表跳過的選項；不是玩家提交的事實為 false。
     */
    @SerialName("rule_decision")
    @Serializable
    data class RuleDecision(
        override val typeKey: String,
        val timing: RuleDecisionTimingDto,
        val stepId: String,
        val stepIndex: Int,
        val nextStepId: String?,
        val actorSeat: Int?,
        val optionId: String?,
        val passed: Boolean,
    ) : HistoryReplayFactDto

    /**
     * 規則選擇流程中完成的一輪公開換牌。
     *
     * @property typeKey 事實種類識別碼。
     * @property timing 選擇視窗開啟的時間點。
     * @property round 輪次，從 1 開始。
     * @property removals 這一輪各玩家移出的牌，依處理順序排列。
     */
    @SerialName("tile_replacement_round")
    @Serializable
    data class TileReplacementRound(
        override val typeKey: String,
        val timing: RuleDecisionTimingDto,
        val round: Int,
        val removals: List<Removal>,
    ) : HistoryReplayFactDto {
        /**
         * 一位玩家在這一輪移出的牌。
         *
         * @property seat 移出牌的玩家座位。
         * @property tiles 移出的牌在本局牌目錄中的索引，依移出順序排列。
         */
        @Serializable
        data class Removal(
            val seat: Int,
            val tiles: List<Int>,
        )
    }

    /**
     * 開局程序的一輪擲骰結果。
     *
     * @property typeKey 事實種類識別碼。
     * @property rollIndex 本局中的零基底擲骰索引。
     * @property actorSeat 實際擲骰玩家的座位。
     * @property dice 逐顆骰子結果。
     */
    @SerialName("dice_rolled")
    @Serializable
    data class DiceRolled(
        override val typeKey: String,
        val rollIndex: Int,
        val actorSeat: Int,
        val dice: List<Int>,
    ) : HistoryReplayFactDto

    /**
     * 局完成結果的安全事實。
     *
     * @property typeKey 事實種類識別碼。
     * @property outcome 局結算結果。
     */
    @SerialName("completion")
    @Serializable
    data class Completion(
        override val typeKey: String,
        val outcome: HistoryRoundOutcomeDto?,
    ) : HistoryReplayFactDto

    /**
     * 規則效果的安全事實。
     *
     * @property typeKey 事實種類識別碼。
     * @property reasonId 規則效果識別碼。
     * @property outcome 局結算結果。
     */
    @SerialName("rule_effect")
    @Serializable
    data class RuleEffect(
        override val typeKey: String,
        val reasonId: String,
        val outcome: HistoryRoundOutcomeDto?,
    ) : HistoryReplayFactDto

    /**
     * 未解讀事實的識別及安全牌參照。
     *
     * @property typeKey 事實種類識別碼。
     * @property actorSeat 已知行為者座位。
     * @property directTiles 已知直接涉及的牌索引。
     * @property revealedTiles 已知新公開的牌索引。
     */
    @SerialName("opaque")
    @Serializable
    data class Opaque(
        override val typeKey: String,
        val actorSeat: Int?,
        val directTiles: List<Int>,
        val revealedTiles: List<Int>,
    ) : HistoryReplayFactDto
}
