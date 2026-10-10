package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [Tile] 的完整 persistence DTO。 */
@Serializable
sealed interface TilePersistenceDto {
    /**
     * [Tile.Numeric] 的 persistence DTO。
     *
     * @property suit 數字牌所屬的花色。
     * @property value 數字牌的牌面數值。
     */
    @Serializable
    @SerialName("numeric")
    /**
     * Numeric 的 persistence 資料型別。
     *
     * @property suit 數牌的花色。
     * @property value 保存的型別化值。
     */
    data class Numeric(val suit: SuitPersistenceDto, val value: Int) : TilePersistenceDto

    /**
     * [Tile.Extension] 的 persistence DTO。
     *
     * @property typeId 擴充牌種的穩定識別字串。
     */
    @Serializable
    @SerialName("extension")
    /**
     * Extension 的 persistence 資料型別。
     *
     * @property typeId 第三方型別的穩定識別碼。
     */
    data class Extension(val typeId: String) : TilePersistenceDto

    /**
     * [Tile.Honor] 的 persistence DTO。
     *
     * @property value 字牌的種類。
     */
    @Serializable
    @SerialName("honor")
    /**
     * Honor 的 persistence 資料型別。
     *
     * @property value 保存的型別化值。
     */
    data class Honor(val value: HonorPersistenceValue) : TilePersistenceDto
}
