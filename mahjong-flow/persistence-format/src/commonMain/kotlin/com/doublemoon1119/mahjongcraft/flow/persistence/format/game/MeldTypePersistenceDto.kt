package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [MeldType] 的 persistence DTO。 */
@Serializable
sealed interface MeldTypePersistenceDto {
    /** 吃牌形成的副露種類。 */
    @Serializable
    @SerialName("chi")
    data object Chi : MeldTypePersistenceDto

    /** 碰牌形成的副露種類。 */
    @Serializable
    @SerialName("pon")
    data object Pon : MeldTypePersistenceDto

    /** 明槓形成的副露種類。 */
    @Serializable
    @SerialName("open_kan")
    data object OpenKan : MeldTypePersistenceDto

    /** 暗槓形成的副露種類。 */
    @Serializable
    @SerialName("closed_kan")
    data object ClosedKan : MeldTypePersistenceDto

    /** 加槓形成的副露種類。 */
    @Serializable
    @SerialName("added_kan")
    data object AddedKan : MeldTypePersistenceDto

    /**
     * 擴充規則提供的副露種類。
     *
     * @property typeId 擴充副露種類的穩定識別字串。
     */
    @Serializable
    @SerialName("extension")
    data class Extension(val typeId: String) : MeldTypePersistenceDto
}
