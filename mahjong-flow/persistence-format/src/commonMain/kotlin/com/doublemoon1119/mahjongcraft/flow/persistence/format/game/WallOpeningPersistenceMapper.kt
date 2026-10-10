package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 將 [WallOpening] 轉換成 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun WallOpening.toPersistenceDto(): WallOpeningPersistenceDto = WallOpeningPersistenceDto(
    wallSideOffsetFromDealer = wallSideOffsetFromDealer,
    stacksFromRight = stacksFromRight,
)

/**
 * 將 [WallOpeningPersistenceDto] 還原成 [WallOpening]。
 *
 * @return 轉換後的結果。
 */
fun WallOpeningPersistenceDto.toDomain(): WallOpening = WallOpening(
    wallSideOffsetFromDealer = wallSideOffsetFromDealer,
    stacksFromRight = stacksFromRight,
)
