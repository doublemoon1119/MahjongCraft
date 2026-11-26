package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlinx.serialization.Serializable

/** [RelativeDirection] 的網路 DTO。 */
@Serializable
sealed interface RelativeDirectionDto {
    /** 位於自身左側的座位。 */
    @Serializable data object Left : RelativeDirectionDto

    /** 位於自身對面的座位。 */
    @Serializable data object Across : RelativeDirectionDto

    /** 位於自身右側的座位。 */
    @Serializable data object Right : RelativeDirectionDto

    /** 自身座位。 */
    @Serializable data object Self : RelativeDirectionDto
}
