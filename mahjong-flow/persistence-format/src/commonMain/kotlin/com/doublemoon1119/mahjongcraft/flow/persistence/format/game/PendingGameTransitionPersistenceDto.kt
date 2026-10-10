package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * [PendingGameTransition] 的持久化表示。
 *
 * 領域層保留 sealed interface 作為可擴充的強型別流程；DTO 目前只保存分支類型，
 * 未來某個分支需要參數時再新增 payload 欄位。
 *
 * @property type 可持久化的待完成流程類型。
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
