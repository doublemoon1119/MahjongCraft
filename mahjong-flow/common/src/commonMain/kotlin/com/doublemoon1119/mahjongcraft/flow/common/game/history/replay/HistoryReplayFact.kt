package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/** 歷史語意事實。 */
sealed interface HistoryReplayFact {
    /** 事實種類識別碼。 */
    val typeKey: String

    /**
     * 已接受動作。
     *
     * @property typeKey 動作事實種類。
     * @property actorSeat 發起者座位。
     * @property actionType 動作種類。
     * @property directTiles 直接涉及的牌。
     * @property revealedTiles 新公開的牌。
     * @property extensionTypeId 擴充動作種類。
     */
    data class KnownAction(
        override val typeKey: String,
        val actorSeat: Int?,
        val actionType: String,
        val directTiles: List<HistoryTileReference>,
        val revealedTiles: List<HistoryTileReference>,
        val extensionTypeId: String?,
    ) : HistoryReplayFact

    /**
     * 動作成立當下的點數收付。
     *
     * @property typeKey 事實種類。
     * @property actorSeat 執行動作的玩家座位。
     * @property actionType 觸發收付的動作種類。
     * @property extensionTypeId 擴充動作種類；內建動作為 null。
     * @property scoreDeltasBySeat 各座位的點數增減。
     * @property potDeposit 放進規則點數池的點數，正數為放入、負數為取出。
     */
    data class ActionScored(
        override val typeKey: String,
        val actorSeat: Int?,
        val actionType: String,
        val extensionTypeId: String?,
        val scoreDeltasBySeat: Map<Int, Int>,
        val potDeposit: Int,
    ) : HistoryReplayFact

    /**
     * 反應事實。
     *
     * @property typeKey 事實種類。
     * @property actionType 動作種類。
     * @property resolvedActorSeat 裁定玩家座位。
     */
    data class Reaction(
        override val typeKey: String,
        val actionType: String?,
        val resolvedActorSeat: Int?,
    ) : HistoryReplayFact

    /**
     * 規則選擇事實。
     *
     * @property typeKey 事實種類。
     * @property stepId 步驟識別碼。
     * @property stepIndex 步驟索引。
     * @property nextStepId 下一步識別碼。
     */
    data class RuleDecision(
        override val typeKey: String,
        val stepId: String,
        val stepIndex: Int,
        val nextStepId: String?,
    ) : HistoryReplayFact

    /**
     * 開局程序的一輪擲骰結果。
     *
     * @property typeKey 事實種類。
     * @property rollIndex 本局中的零基底擲骰索引。
     * @property actorSeat 實際擲骰玩家的座位。
     * @property dice 逐顆骰子結果。
     */
    data class DiceRolled(
        override val typeKey: String,
        val rollIndex: Int,
        val actorSeat: Int,
        val dice: List<Int>,
    ) : HistoryReplayFact

    /**
     * 完成事實。
     *
     * @property typeKey 事實種類。
     * @property outcome 局結算結果。
     */
    data class Completion(override val typeKey: String, val outcome: HistoryRoundOutcome?) : HistoryReplayFact

    /**
     * 已完成的規則效果。
     *
     * @property typeKey 事實種類。
     * @property reasonId 規則效果識別碼。
     * @property outcome 該效果同時完成本局時保存的結算結果。
     */
    data class RuleEffect(
        override val typeKey: String,
        val reasonId: String,
        val outcome: HistoryRoundOutcome?,
    ) : HistoryReplayFact

    /**
     * 未知擴充事實，只保留識別碼與可驗證的公開參照，不保留私有 payload。
     *
     * @property typeKey 穩定種類識別碼。
     * @property actorSeat 已知行為者的初始座位。
     * @property directTiles 已知直接涉及的局內牌參照。
     * @property revealedTiles 已知新公開的局內牌參照。
     */
    data class Opaque(
        override val typeKey: String,
        val actorSeat: Int? = null,
        val directTiles: List<HistoryTileReference> = emptyList(),
        val revealedTiles: List<HistoryTileReference> = emptyList(),
    ) : HistoryReplayFact
}
