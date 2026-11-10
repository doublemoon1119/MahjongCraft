package com.doublemoon1119.mahjongcraft.flow.network.dto.model
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun MeldType.toDto(): MeldTypeDto = when (this) {
    MeldType.Chi -> MeldTypeDto.Chi
    MeldType.Pon -> MeldTypeDto.Pon
    MeldType.OpenKan -> MeldTypeDto.OpenKan
    MeldType.ClosedKan -> MeldTypeDto.ClosedKan
    MeldType.AddedKan -> MeldTypeDto.AddedKan
    is MeldType.Extension -> MeldTypeDto.Extension(typeId.toString())
}

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun MeldTypeDto.toDomain(): MeldType = when (this) {
    MeldTypeDto.Chi -> MeldType.Chi
    MeldTypeDto.Pon -> MeldType.Pon
    MeldTypeDto.OpenKan -> MeldType.OpenKan
    MeldTypeDto.ClosedKan -> MeldType.ClosedKan
    MeldTypeDto.AddedKan -> MeldType.AddedKan
    is MeldTypeDto.Extension -> MeldType.Extension(MeldTypeId.parse(typeId))
}
