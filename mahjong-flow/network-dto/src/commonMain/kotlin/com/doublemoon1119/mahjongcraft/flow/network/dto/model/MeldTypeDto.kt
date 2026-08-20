package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import kotlinx.serialization.Serializable

/** [MeldType] 的網路 DTO。 */
@Serializable
enum class MeldTypeDto { CHI, PON, OPEN_KAN, CLOSED_KAN, ADDED_KAN }

fun MeldType.toDto(): MeldTypeDto = when (this) {
    MeldType.CHI -> MeldTypeDto.CHI
    MeldType.PON -> MeldTypeDto.PON
    MeldType.OPEN_KAN -> MeldTypeDto.OPEN_KAN
    MeldType.CLOSED_KAN -> MeldTypeDto.CLOSED_KAN
    MeldType.ADDED_KAN -> MeldTypeDto.ADDED_KAN
}

fun MeldTypeDto.toDomain(): MeldType = when (this) {
    MeldTypeDto.CHI -> MeldType.CHI
    MeldTypeDto.PON -> MeldType.PON
    MeldTypeDto.OPEN_KAN -> MeldType.OPEN_KAN
    MeldTypeDto.CLOSED_KAN -> MeldType.CLOSED_KAN
    MeldTypeDto.ADDED_KAN -> MeldType.ADDED_KAN
}
