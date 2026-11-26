package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import kotlinx.serialization.Serializable

/** [MeldType] 的網路 DTO。 */
@Serializable
sealed interface MeldTypeDto {
    /** 吃牌形成的面子。 */
    @Serializable data object Chi : MeldTypeDto

    /** 碰牌形成的面子。 */
    @Serializable data object Pon : MeldTypeDto

    /** 以其他玩家的牌完成的明槓。 */
    @Serializable data object OpenKan : MeldTypeDto

    /** 以手牌完成的暗槓。 */
    @Serializable data object ClosedKan : MeldTypeDto

    /** 將既有碰子加牌升級為槓。 */
    @Serializable data object AddedKan : MeldTypeDto

    /**
     * Extension 網路 DTO。
     *
     * @property typeId 擴充類型識別碼。
     */
    @Serializable data class Extension(val typeId: String) : MeldTypeDto
}
