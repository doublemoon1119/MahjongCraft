package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.table.RoundConclusion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [RoundConclusion] 的 persistence DTO。 */
@Serializable
sealed interface RoundConclusionPersistenceDto {
    /**
     * [RoundConclusion.Win] 的 persistence DTO。
     *
     * @property winnerPlayerIds 胡牌的玩家。
     */
    @Serializable
    @SerialName("win")
    data class Win(val winnerPlayerIds: Set<String>) : RoundConclusionPersistenceDto

    /**
     * [RoundConclusion.ExhaustiveDraw] 的 persistence DTO。
     *
     * @property reason 流局原因。
     * @property tenpaiPlayerIds 流局時聽牌的玩家。
     */
    @Serializable
    @SerialName("exhaustive_draw")
    data class ExhaustiveDraw(
        val reason: TypedPersistenceDto,
        val tenpaiPlayerIds: Set<String>,
    ) : RoundConclusionPersistenceDto

    /**
     * [RoundConclusion.AbortiveDraw] 的 persistence DTO。
     *
     * @property reason 流局原因。
     */
    @Serializable
    @SerialName("abortive_draw")
    data class AbortiveDraw(val reason: TypedPersistenceDto) : RoundConclusionPersistenceDto
}
