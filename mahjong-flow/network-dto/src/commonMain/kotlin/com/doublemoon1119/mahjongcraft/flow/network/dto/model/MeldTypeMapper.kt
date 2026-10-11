package com.doublemoon1119.mahjongcraft.flow.network.dto.model
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun MeldType.toDto(): MeldTypeDto = when (this) {
    MeldType.CHI -> MeldTypeDto.Chi
    MeldType.PON -> MeldTypeDto.Pon
    MeldType.OPEN_KAN -> MeldTypeDto.OpenKan
    MeldType.CLOSED_KAN -> MeldTypeDto.ClosedKan
    MeldType.ADDED_KAN -> MeldTypeDto.AddedKan
    is MeldType.Extension -> MeldTypeDto.Extension(typeId.toString())
}

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun MeldTypeDto.toDomain(): MeldType = when (this) {
    MeldTypeDto.Chi -> MeldType.CHI
    MeldTypeDto.Pon -> MeldType.PON
    MeldTypeDto.OpenKan -> MeldType.OPEN_KAN
    MeldTypeDto.ClosedKan -> MeldType.CLOSED_KAN
    MeldTypeDto.AddedKan -> MeldType.ADDED_KAN
    is MeldTypeDto.Extension -> MeldType.Extension(MeldTypeId.parse(typeId))
}
