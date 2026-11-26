package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [TurnPhase] 的 persistence DTO。 */
@Serializable
sealed interface TurnPhasePersistenceDto {
    /** [TurnPhase.AwaitingDraw] 的 persistence DTO。 */
    @Serializable
    @SerialName(TurnPhaseSerialNames.AWAITING_DRAW)
    data object AwaitingDraw : TurnPhasePersistenceDto

    /** [TurnPhase.AwaitingAction] 的 persistence DTO。 */
    @Serializable
    @SerialName(TurnPhaseSerialNames.AWAITING_ACTION)
    data object AwaitingAction : TurnPhasePersistenceDto

    /** [TurnPhase.AwaitingDiscard] 的 persistence DTO。 */
    @Serializable
    @SerialName(TurnPhaseSerialNames.AWAITING_DISCARD)
    data object AwaitingDiscard : TurnPhasePersistenceDto

    /**
     * [TurnPhase.AwaitingDiscardReaction] 的 persistence DTO。
     *
     * @property reaction 捨牌反應。
     */
    @Serializable
    @SerialName(TurnPhaseSerialNames.AWAITING_DISCARD_REACTION)
    data class AwaitingDiscardReaction(val reaction: PendingReactionPersistenceDto) : TurnPhasePersistenceDto

    /**
     * [TurnPhase.AwaitingRobbingReaction] 的 persistence DTO。
     *
     * @property reaction 搶和反應。
     */
    @Serializable
    @SerialName(TurnPhaseSerialNames.AWAITING_ROBBING_REACTION)
    data class AwaitingRobbingReaction(val reaction: PendingRobbingReactionPersistenceDto) : TurnPhasePersistenceDto

    /**
     * [TurnPhase.RoundOver] 的 persistence DTO。
     *
     * @property conclusion 本局結束的方式。
     */
    @Serializable
    @SerialName(TurnPhaseSerialNames.ROUND_OVER)
    data class RoundOver(val conclusion: RoundConclusionPersistenceDto) : TurnPhasePersistenceDto
}
