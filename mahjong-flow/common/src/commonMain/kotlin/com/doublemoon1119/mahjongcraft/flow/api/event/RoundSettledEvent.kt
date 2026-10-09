package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 一次結算完成：和牌、流局（含途中流局）或規則的特殊結果。和牌後本局繼續時也會發出，因此不代表這一局已經結束。
 *
 * 同一次結算只發出一次；一炮多響是同一次結算，所有贏家都在 [beneficiaryPlayerIds] 中。
 *
 * @property roundNumber 這一局在場次中的局數，從 1 開始。
 * @property outcomeId 結算結果的 namespaced ID：和牌為結果 ID（例如自摸、榮和），流局為流局原因 ID，特殊結果為規則提供的
 *   結果 ID。
 * @property kind 結算的種類。
 * @property beneficiaryPlayerIds 得利的玩家，例如贏家或流局時聽牌的玩家；途中流局沒有得利的玩家。
 * @property responsiblePlayerIds 負責的玩家，例如放銃者；沒有時為空集合。
 * @property players 依座位順序排列的每位玩家結算前後的分數與名次。
 */
class RoundSettledEvent private constructor(
    override val eventId: Uuid,
    override val matchId: Uuid,
    override val venueId: Uuid,
    override val ruleModuleId: String,
    val roundNumber: Int,
    val outcomeId: String,
    val kind: RoundSettlementKind,
    val beneficiaryPlayerIds: Set<Uuid>,
    val responsiblePlayerIds: Set<Uuid>,
    val players: List<RoundScoreChange>,
) : MatchEvent {
    /** 事件系統內部建立事件的入口。 */
    internal companion object {
        /**
         * 建立事件，所有集合複製為無法修改的快照。
         *
         * @param eventId 事件的唯一識別碼。
         * @param matchId 場次 UUID。
         * @param venueId 場地 UUID。
         * @param ruleModuleId 對局規則模組的 namespaced ID。
         * @param roundNumber 這一局在場次中的局數。
         * @param outcomeId 結算結果的 namespaced ID。
         * @param kind 結算的種類。
         * @param beneficiaryPlayerIds 得利的玩家 UUID。
         * @param responsiblePlayerIds 負責的玩家 UUID。
         * @param players 依座位順序排列的玩家結算前後資料。
         * @return 建立的一局結算事件。
         */
        @Suppress("LongParameterList")
        @JvmSynthetic
        fun create(
            eventId: Uuid,
            matchId: Uuid,
            venueId: Uuid,
            ruleModuleId: String,
            roundNumber: Int,
            outcomeId: String,
            kind: RoundSettlementKind,
            beneficiaryPlayerIds: Set<Uuid>,
            responsiblePlayerIds: Set<Uuid>,
            players: List<RoundScoreChange>,
        ): RoundSettledEvent = RoundSettledEvent(
            eventId = eventId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = ruleModuleId,
            roundNumber = roundNumber,
            outcomeId = outcomeId,
            kind = kind,
            beneficiaryPlayerIds = ReadOnlySetSnapshot(beneficiaryPlayerIds),
            responsiblePlayerIds = ReadOnlySetSnapshot(responsiblePlayerIds),
            players = ReadOnlyListSnapshot(players),
        )
    }
}
