package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 麻將牌 [com.doublemoon1119.mahjongcraft.logic.base.Tile] 的完整存檔 DTO。 */
@Serializable
sealed interface TilePersistenceDto {
    /**
     * 數字牌的存檔 DTO。
     *
     * @property suit 數字牌所屬的花色。
     * @property value 數字牌的牌面數值。
     */
    @Serializable
    @SerialName("numeric")
    data class Numeric(val suit: SuitPersistenceDto, val value: Int) : TilePersistenceDto

    /**
     * 擴充牌種的存檔 DTO。
     *
     * @property typeId 擴充牌種的穩定識別字串。
     */
    @Serializable
    @SerialName("extension")
    data class Extension(val typeId: String) : TilePersistenceDto

    /**
     * 字牌的存檔 DTO。
     *
     * @property value 字牌的種類。
     */
    @Serializable
    @SerialName("honor")
    data class Honor(val value: HonorPersistenceValue) : TilePersistenceDto
}
