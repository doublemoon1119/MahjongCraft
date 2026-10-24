package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 一場對局結束：正常打完或中途終止。
 *
 * @property completion 正常打完或中途終止。
 * @property reasonId 終局原因的 namespaced ID；中途終止時為終止原因。
 * @property standings 依名次排列的每位玩家最終分數與名次。
 * @property roundContext 終局前最後一局的局數與場風；沒有可提供的局況時為 `null`。
 * @property publicMarkers 規則提供的公開終局標記。
 * @property completedPlayerIds 在正常完成的終局中完成對局的玩家 UUID；沒有時為空集合。
 */
class MatchEndedEvent private constructor(
    override val eventId: Uuid,
    override val matchId: Uuid,
    override val venueId: Uuid,
    override val ruleModuleId: String,
    val completion: MatchCompletion,
    val reasonId: String,
    val standings: List<MatchStanding>,
    val roundContext: EventRoundContext?,
    val publicMarkers: List<PublicEventMarker>,
    val completedPlayerIds: Set<Uuid>,
) : MatchEvent {
    /** 事件系統內部建立事件的入口。 */
    internal companion object {
        /**
         * 建立事件，[standings] 複製為無法修改的快照。
         *
         * @param eventId 事件的唯一識別碼。
         * @param matchId 場次 UUID。
         * @param venueId 場地 UUID。
         * @param ruleModuleId 對局規則模組的 namespaced ID。
         * @param completion 對局結束的方式。
         * @param reasonId 終局原因的 namespaced ID。
         * @param standings 依名次排列的玩家最終分數與名次。
         * @param roundContext 終局前最後一局的局數與場風；沒有時為 `null`。
         * @param publicMarkers 規則提供的公開終局標記。
         * @param completedPlayerIds 完成對局的玩家 UUID。
         * @return 建立的對局結束事件。
         */
        @JvmSynthetic
        fun create(
            eventId: Uuid,
            matchId: Uuid,
            venueId: Uuid,
            ruleModuleId: String,
            completion: MatchCompletion,
            reasonId: String,
            standings: List<MatchStanding>,
            roundContext: EventRoundContext? = null,
            publicMarkers: List<PublicEventMarker> = emptyList(),
            completedPlayerIds: Set<Uuid> = emptySet(),
        ): MatchEndedEvent = MatchEndedEvent(
            eventId = eventId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = ruleModuleId,
            completion = completion,
            reasonId = reasonId,
            standings = ReadOnlyListSnapshot(standings),
            roundContext = roundContext,
            publicMarkers = ReadOnlyListSnapshot(publicMarkers),
            completedPlayerIds = ReadOnlySetSnapshot(completedPlayerIds),
        )
    }
}
