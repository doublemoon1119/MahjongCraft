package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlinx.serialization.Serializable

/**
 * [Tile] 的網路 DTO。地區限定牌種（如花牌）一律以 [Extension] 承載穩定牌種 ID。
 */
@Serializable
sealed interface TileDto {
    /**
     * 數字牌的網路 DTO，包含花色與牌面數值。
     *
     * @property suit 牌的花色。
     * @property value 擴充值或牌面數值。
     */
    @Serializable
    data class Numeric(val suit: SuitDto, val value: Int) : TileDto

    /**
     * Extension 網路 DTO。
     *
     * @property typeId 擴充類型識別碼。
     */
    @Serializable
    data class Extension(val typeId: String) : TileDto

    @Serializable
    sealed interface Honor : TileDto {
        @Serializable data object East : Honor

        @Serializable data object South : Honor

        @Serializable data object West : Honor

        @Serializable data object North : Honor

        @Serializable data object Red : Honor

        @Serializable data object Green : Honor

        @Serializable data object White : Honor
    }
}
