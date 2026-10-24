package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 一局正式完成，且不會再因本局選擇產生新的流程。
 *
 * @property eventId 事件的唯一識別碼。
 * @property matchId 場次 UUID。
 * @property venueId 場地 UUID。
 * @property ruleModuleId 對局規則模組的 namespaced ID。
 * @property roundContext 完成本局時的局數與場風。
 * @property classification 本局完成原因的中立分類。
 * @property outcomeId 本局結果的完整 namespaced ID。
 * @property players 依座位順序排列的完成時分數與名次；此時已結算，前後值相同，分數異動請讀取 [RoundSettledEvent]。
 */
class RoundCompletedEvent private constructor(
    override val eventId: Uuid,
    override val matchId: Uuid,
    override val venueId: Uuid,
    override val ruleModuleId: String,
    val roundContext: EventRoundContext,
    val classification: RoundCompletionKind,
    val outcomeId: String,
    val players: List<RoundScoreChange>,
) : MatchEvent {
    /** 事件系統內部建立事件的入口。 */
    internal companion object {
        /**
         * 建立本局完成事件，並複製玩家清單。
         *
         * @param eventId 事件的唯一識別碼。
         * @param matchId 場次 UUID。
         * @param venueId 場地 UUID。
         * @param ruleModuleId 對局規則模組的 namespaced ID。
         * @param roundContext 完成本局時的局數與場風。
         * @param classification 本局完成原因。
         * @param outcomeId 本局結果的完整 namespaced ID。
         * @param players 依座位順序排列的玩家結算資料。
         * @return 建立的本局完成事件。
         */
        @JvmSynthetic
        fun create(
            eventId: Uuid,
            matchId: Uuid,
            venueId: Uuid,
            ruleModuleId: String,
            roundContext: EventRoundContext,
            classification: RoundCompletionKind,
            outcomeId: String,
            players: List<RoundScoreChange>,
        ): RoundCompletedEvent = RoundCompletedEvent(
            eventId = eventId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = ruleModuleId,
            roundContext = roundContext,
            classification = classification,
            outcomeId = outcomeId,
            players = ReadOnlyListSnapshot(players),
        )
    }
}
