package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlinx.serialization.Serializable

/** [WallOpening] 的完整 persistence DTO。 */
@Serializable
data class WallOpeningPersistenceDto(
    val wallSideOffsetFromDealer: Int,
    val stacksFromRight: Int,
)

/** 將 [WallOpening] 轉換成 persistence DTO。 */
fun WallOpening.toPersistenceDto(): WallOpeningPersistenceDto = WallOpeningPersistenceDto(
    wallSideOffsetFromDealer = wallSideOffsetFromDealer,
    stacksFromRight = stacksFromRight,
)

/** 將 [WallOpeningPersistenceDto] 還原成 [WallOpening]。 */
fun WallOpeningPersistenceDto.toDomain(): WallOpening = WallOpening(
    wallSideOffsetFromDealer = wallSideOffsetFromDealer,
    stacksFromRight = stacksFromRight,
)
