package com.doublemoon1119.mahjongcraft.flow.network.dto.message

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
     * 局準備步驟的安全事實。
     *
     * @property typeKey 事實種類識別碼。
     * @property stepId 步驟識別碼。
     * @property stepIndex 步驟索引。
     * @property nextStepId 下一步識別碼。
     */
    @SerialName("preparation")
    @Serializable
    data class Preparation(
        override val typeKey: String,
        val stepId: String,
        val stepIndex: Int,
        val nextStepId: String?,
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
