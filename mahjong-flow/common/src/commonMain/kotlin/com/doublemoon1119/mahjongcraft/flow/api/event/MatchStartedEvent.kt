package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 一場對局開始。
 *
 * @property players 依座位順序排列的玩家。
 */
class MatchStartedEvent private constructor(
    override val eventId: Uuid,
    override val matchId: Uuid,
    override val venueId: Uuid,
    override val ruleModuleId: String,
    val players: List<MatchPlayer>,
) : MatchEvent {
    /** 事件系統內部建立事件的入口。 */
    internal companion object {
        /**
         * 建立事件，[players] 複製為無法修改的快照。
         *
         * @param eventId 事件的唯一識別碼。
         * @param matchId 場次 UUID。
         * @param venueId 場地 UUID。
         * @param ruleModuleId 對局規則模組的 namespaced ID。
         * @param players 依座位順序排列的玩家。
         * @return 建立的對局開始事件。
         */
        @JvmSynthetic
        fun create(
            eventId: Uuid,
            matchId: Uuid,
            venueId: Uuid,
            ruleModuleId: String,
            players: List<MatchPlayer>,
        ): MatchStartedEvent = MatchStartedEvent(
            eventId = eventId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = ruleModuleId,
            players = ReadOnlyListSnapshot(players),
        )
    }
}
