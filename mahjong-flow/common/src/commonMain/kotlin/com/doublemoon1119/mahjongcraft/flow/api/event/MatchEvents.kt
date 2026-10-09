package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 對局中發生、供第三方訂閱的事件。
 *
 * 事件與其中的資料只由 MahjongCraft 建立：建構子不公開，建立入口對 Java 不可見；第三方只讀取屬性，不建立也不實作這些
 * 型別。所有集合屬性都是建立時複製、無法修改的快照。之後只以新增唯讀屬性擴充。
 */
sealed interface MatchEvent {
    /** 事件的唯一識別碼，供需要去重的訂閱者使用。 */
    val eventId: Uuid

    /** 場次 UUID，關聯同一場對局的事件；同一場會有多個事件，不是事件的唯一識別碼。 */
    val matchId: Uuid

    /** 場地 UUID；同一場地重新開局時不變。 */
    val venueId: Uuid

    /** 對局規則模組的 namespaced ID。 */
    val ruleModuleId: String
}

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
    /** MahjongCraft 內部建立事件的入口。 */
    internal companion object {
        /** 建立事件，[players] 複製為無法修改的快照。 */
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
    /** MahjongCraft 內部建立事件的入口。 */
    internal companion object {
        /** 建立事件，所有集合複製為無法修改的快照。 */
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

/**
 * 一場對局結束：正常打完或中途終止。
 *
 * @property completion 正常打完或中途終止。
 * @property reasonId 終局原因的 namespaced ID；中途終止時為終止原因。
 * @property standings 依名次排列的每位玩家最終分數與名次。
 */
class MatchEndedEvent private constructor(
    override val eventId: Uuid,
    override val matchId: Uuid,
    override val venueId: Uuid,
    override val ruleModuleId: String,
    val completion: MatchCompletion,
    val reasonId: String,
    val standings: List<MatchStanding>,
) : MatchEvent {
    /** MahjongCraft 內部建立事件的入口。 */
    internal companion object {
        /** 建立事件，[standings] 複製為無法修改的快照。 */
        @JvmSynthetic
        fun create(
            eventId: Uuid,
            matchId: Uuid,
            venueId: Uuid,
            ruleModuleId: String,
            completion: MatchCompletion,
            reasonId: String,
            standings: List<MatchStanding>,
        ): MatchEndedEvent = MatchEndedEvent(
            eventId = eventId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = ruleModuleId,
            completion = completion,
            reasonId = reasonId,
            standings = ReadOnlyListSnapshot(standings),
        )
    }
}

/**
 * 對局中的一名玩家。
 *
 * @property playerId 玩家 UUID。
 * @property isAi 是否由 AI 操控。
 * @property initialSeatIndex 起家座位，從 0 開始。
 */
class MatchPlayer private constructor(
    val playerId: Uuid,
    val isAi: Boolean,
    val initialSeatIndex: Int,
) {
    /** MahjongCraft 內部建立資料的入口。 */
    internal companion object {
        /** 建立一名玩家的資料。 */
        @JvmSynthetic
        fun create(
            playerId: Uuid,
            isAi: Boolean,
            initialSeatIndex: Int,
        ): MatchPlayer = MatchPlayer(playerId, isAi, initialSeatIndex)
    }
}

/**
 * 一名玩家在一次結算前後的分數與名次。名次依規則的回合排名決定，從 1 開始。
 *
 * @property playerId 玩家 UUID。
 * @property previousScore 結算前的分數。
 * @property currentScore 結算後的分數。
 * @property previousRank 結算前的名次。
 * @property currentRank 結算後的名次。
 */
class RoundScoreChange private constructor(
    val playerId: Uuid,
    val previousScore: Int,
    val currentScore: Int,
    val previousRank: Int,
    val currentRank: Int,
) {
    /** MahjongCraft 內部建立資料的入口。 */
    internal companion object {
        /** 建立一名玩家結算前後的資料。 */
        @JvmSynthetic
        fun create(
            playerId: Uuid,
            previousScore: Int,
            currentScore: Int,
            previousRank: Int,
            currentRank: Int,
        ): RoundScoreChange = RoundScoreChange(playerId, previousScore, currentScore, previousRank, currentRank)
    }
}

/**
 * 一名玩家在對局結束時的分數與名次。名次依規則的終局排名決定（包含同分時的判定），從 1 開始。
 *
 * @property playerId 玩家 UUID。
 * @property isAi 是否由 AI 操控。
 * @property score 最終分數。
 * @property rank 最終名次。
 */
class MatchStanding private constructor(
    val playerId: Uuid,
    val isAi: Boolean,
    val score: Int,
    val rank: Int,
) {
    /** MahjongCraft 內部建立資料的入口。 */
    internal companion object {
        /** 建立一名玩家的最終分數與名次。 */
        @JvmSynthetic
        fun create(
            playerId: Uuid,
            isAi: Boolean,
            score: Int,
            rank: Int,
        ): MatchStanding = MatchStanding(playerId, isAi, score, rank)
    }
}

/** 結算的種類。 */
enum class RoundSettlementKind {
    /** 和牌。 */
    WIN,

    /** 流局，包含途中流局。 */
    DRAW,

    /** 規則提供的特殊結果，例如日麻的流局滿貫。 */
    SPECIAL,
}

/** 對局結束的方式。 */
enum class MatchCompletion {
    /** 依規則正常打完。 */
    COMPLETED,

    /** 尚未打完就終止，例如場地在對局中被移除。 */
    ABORTED,
}
