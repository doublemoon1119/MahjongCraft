package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * 待完成流程 [com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition] 的存檔表示。
 *
 * 保存尚待執行的流程類型。
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
        /** 推進到下一局。 */
        ADVANCE_ROUND,

        /** 返回房間。 */
        RETURN_TO_ROOM,
    }
}
