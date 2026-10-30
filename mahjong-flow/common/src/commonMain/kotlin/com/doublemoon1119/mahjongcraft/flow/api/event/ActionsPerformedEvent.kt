package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 一批同一筆提交中已成立的公開遊戲動作。
 *
 * 一般摸牌不單獨產生事件；事件只包含需要讓訂閱者知道的成立動作。
 *
 * @property eventId 事件的唯一識別碼。
 * @property matchId 場次 UUID。
 * @property venueId 場地 UUID。
 * @property ruleModuleId 對局規則模組的 namespaced ID。
 * @property roundNumber 這些動作所在的局數。
 * @property actions 依發生順序排列的已成立動作。
 */
class ActionsPerformedEvent private constructor(
    override val eventId: Uuid,
    override val matchId: Uuid,
    override val venueId: Uuid,
    override val ruleModuleId: String,
    val roundNumber: Int,
    val actions: List<PerformedAction>,
) : MatchEvent {
    /** 事件系統內部建立事件的入口。 */
    internal companion object {
        /**
         * 建立動作事件，並複製動作清單。
         *
         * @param eventId 事件的唯一識別碼。
         * @param matchId 場次 UUID。
         * @param venueId 場地 UUID。
         * @param ruleModuleId 對局規則模組的 namespaced ID。
         * @param roundNumber 動作所在的局數。
         * @param actions 依發生順序排列的動作。
         * @return 建立的動作事件。
         */
        @JvmSynthetic
        fun create(
            eventId: Uuid,
            matchId: Uuid,
            venueId: Uuid,
            ruleModuleId: String,
            roundNumber: Int,
            actions: List<PerformedAction>,
        ): ActionsPerformedEvent = ActionsPerformedEvent(
            eventId = eventId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = ruleModuleId,
            roundNumber = roundNumber,
            actions = ReadOnlyListSnapshot(actions),
        )
    }
}
