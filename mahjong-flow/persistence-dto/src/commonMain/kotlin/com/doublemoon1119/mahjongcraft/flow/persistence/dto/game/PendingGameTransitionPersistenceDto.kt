package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import kotlinx.serialization.Serializable

/**
 * [PendingGameTransition] 的持久化表示。
 *
 * 領域層保留 sealed interface 作為可擴充的強型別流程；DTO 目前只保存分支類型，
 * 未來某個分支需要參數時可新增具有預設值的 payload 欄位，維持舊存檔相容。
 */
@Serializable
data class PendingGameTransitionPersistenceDto(
    val type: Type,
) {
    /** 可持久化的待完成流程類型。 */
    @Serializable
    enum class Type {
        ADVANCE_ROUND,
        RETURN_TO_ROOM,
    }
}

/** 將領域層待完成流程轉換為 persistence DTO。 */
fun PendingGameTransition.toPersistenceDto(): PendingGameTransitionPersistenceDto = PendingGameTransitionPersistenceDto(
    type = when (this) {
        PendingGameTransition.AdvanceRound -> PendingGameTransitionPersistenceDto.Type.ADVANCE_ROUND
        PendingGameTransition.ReturnToRoom -> PendingGameTransitionPersistenceDto.Type.RETURN_TO_ROOM
    },
)

/** 將 persistence DTO 還原為領域層待完成流程。 */
fun PendingGameTransitionPersistenceDto.toDomain(): PendingGameTransition = when (type) {
    PendingGameTransitionPersistenceDto.Type.ADVANCE_ROUND -> PendingGameTransition.AdvanceRound
    PendingGameTransitionPersistenceDto.Type.RETURN_TO_ROOM -> PendingGameTransition.ReturnToRoom
}
