package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 一筆交易中已成立的規則結果；不代表每個結果都是和牌或已完成本局。
 *
 * @property results 依提交順序排列的規則結果。
 */
class RuleResultsResolvedEvent private constructor(
    override val eventId: Uuid,
    override val matchId: Uuid,
    override val venueId: Uuid,
    override val ruleModuleId: String,
    val results: List<RuleResultDetail>,
) : MatchEvent {
    /** 事件系統內部建立事件的入口。 */
    internal companion object {
        /**
         * 建立事件並複製結果清單。
         *
         * @param eventId 事件唯一識別碼。
         * @param matchId 場次 UUID。
         * @param venueId 場地 UUID。
         * @param ruleModuleId 規則模組 ID。
         * @param results 已成立的規則結果。
         * @return 規則結果事件。
         */
        @JvmSynthetic
        fun create(
            eventId: Uuid,
            matchId: Uuid,
            venueId: Uuid,
            ruleModuleId: String,
            results: List<RuleResultDetail>,
        ): RuleResultsResolvedEvent = RuleResultsResolvedEvent(
            eventId = eventId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = ruleModuleId,
            results = ReadOnlyListSnapshot(results),
        )
    }
}
