package com.doublemoon1119.mahjongcraft.logic.table

import kotlin.uuid.Uuid

/** 本局結算後應採用的明確莊家推進決策。 */
enum class RoundTransitionDirective {
    /** 莊家連莊。 */
    REPEAT_DEALER,

    /** 莊家過莊。 */
    ADVANCE_DEALER,
}

/** 本局權威結果的規則中立分類。 */
enum class RoundCompletionClassification {
    /** 胡牌或與胡牌等價的結果。 */
    WIN,

    /** 一般荒牌流局。 */
    EXHAUSTIVE_DRAW,

    /** 途中流局。 */
    ABORTIVE_DRAW,

    /** 第三方規則提供、核心不進一步解讀的結果。 */
    EXTENSION,
}

/**
 * 一局完成後供整場 progression 判定使用的權威摘要。
 *
 * @property outcomeId 完整 namespaced outcome ID。
 * @property classification 規則中立結果分類。
 * @property beneficiaryPlayerIds 此結果的得利者。
 * @property responsiblePlayerIds 此結果的責任玩家。
 * @property transitionDirective 莊家應連莊或過莊的明確決策。
 * @property settledScoresByPlayerId 本局結算後每位玩家的權威分數。
 */
data class RoundCompletionSummary(
    val outcomeId: String,
    val classification: RoundCompletionClassification,
    val beneficiaryPlayerIds: Set<Uuid>,
    val responsiblePlayerIds: Set<Uuid> = emptySet(),
    val transitionDirective: RoundTransitionDirective,
    val settledScoresByPlayerId: Map<Uuid, Int>,
) {
    init {
        require(ID_PATTERN.matches(outcomeId)) { "Round completion outcomeId must be a full namespaced id: $outcomeId" }
        require(beneficiaryPlayerIds.all { it in settledScoresByPlayerId }) { "Round completion beneficiaries must belong to the table" }
        require(responsiblePlayerIds.all { it in settledScoresByPlayerId }) { "Round completion responsible players must belong to the table" }
    }

    private companion object {
        /** Minecraft 慣用 namespace 與 path 的完整識別碼格式。 */
        val ID_PATTERN = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")
    }
}

/** Match progression policy 的純邏輯輸入。 */
data class MatchProgressionContext(
    /** 本局完成分數結算後、尚未開下一局的權威桌況。 */
    val tableState: TableState,
    /** 本局權威結果摘要。 */
    val completion: RoundCompletionSummary,
    /** 由規則的 Match comparator 產生、由第一名到末位的權威玩家順序。 */
    val rankedPlayerIds: List<Uuid>,
) {
    init {
        val playerIds = tableState.players.mapTo(mutableSetOf()) { it.id }
        require(rankedPlayerIds.size == playerIds.size && rankedPlayerIds.toSet() == playerIds) {
            "Match ranking must contain every table player exactly once"
        }
        require(completion.settledScoresByPlayerId.keys == playerIds) {
            "Round completion scores must contain exactly the table players"
        }
    }
}

/** 整場對局在本局結束後的權威決策。 */
sealed interface MatchProgressionDecision {
    /** 立即結束整場對局。 */
    data class EndMatch(val reasonId: String) : MatchProgressionDecision {
        init {
            require(ID_PATTERN.matches(reasonId)) { "Match end reason must be a full namespaced id: $reasonId" }
        }
    }

    /** 繼續整場對局。 */
    data class ContinueMatch(val transition: MatchRoundTransition) : MatchProgressionDecision

    private companion object {
        /** Minecraft 慣用 namespace 與 path 的完整識別碼格式。 */
        val ID_PATTERN = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")
    }
}

/** 繼續整場對局時採用的局位變化。 */
sealed interface MatchRoundTransition {
    /** 保留目前局位並連莊。 */
    data object RepeatCurrentRound : MatchRoundTransition

    /** 過莊並前進到明確的下一局位。 */
    data class AdvanceTo(val nextPosition: MatchRoundPosition) : MatchRoundTransition
}

/** 由規則決定初始局位與每局結算後整場去向的純邏輯 policy。 */
interface MatchProgressionPolicy {
    /** 建立此規則的初始局位。 */
    fun initialPosition(playerCount: Int): MatchRoundPosition = MatchRoundPosition.initial()

    /** 依本局權威結果決定終局、連莊或下一局位。 */
    fun decide(context: MatchProgressionContext): MatchProgressionDecision
}
