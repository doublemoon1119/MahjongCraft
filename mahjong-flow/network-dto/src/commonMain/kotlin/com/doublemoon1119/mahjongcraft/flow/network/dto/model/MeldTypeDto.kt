package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import kotlinx.serialization.Serializable

/** [MeldType] 的網路 DTO。 */
@Serializable
sealed interface MeldTypeDto {
    @Serializable data object Chi : MeldTypeDto

    @Serializable data object Pon : MeldTypeDto

    @Serializable data object OpenKan : MeldTypeDto

    @Serializable data object ClosedKan : MeldTypeDto

    @Serializable data object AddedKan : MeldTypeDto

    /**
     * Extension 網路 DTO。
     *
     * @property typeId 擴充類型識別碼。
     */
    @Serializable data class Extension(val typeId: String) : MeldTypeDto
}
