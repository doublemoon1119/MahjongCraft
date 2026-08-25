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

    @Serializable data class Extension(val typeId: String) : MeldTypeDto
}

fun MeldType.toDto(): MeldTypeDto = when (this) {
    MeldType.CHI -> MeldTypeDto.Chi
    MeldType.PON -> MeldTypeDto.Pon
    MeldType.OPEN_KAN -> MeldTypeDto.OpenKan
    MeldType.CLOSED_KAN -> MeldTypeDto.ClosedKan
    MeldType.ADDED_KAN -> MeldTypeDto.AddedKan
    is MeldType.Extension -> MeldTypeDto.Extension(typeId.toString())
}

fun MeldTypeDto.toDomain(): MeldType = when (this) {
    MeldTypeDto.Chi -> MeldType.CHI
    MeldTypeDto.Pon -> MeldType.PON
    MeldTypeDto.OpenKan -> MeldType.OPEN_KAN
    MeldTypeDto.ClosedKan -> MeldType.CLOSED_KAN
    MeldTypeDto.AddedKan -> MeldType.ADDED_KAN
    is MeldTypeDto.Extension -> MeldType.Extension(com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId.parse(typeId))
}
