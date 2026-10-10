package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile

/**
 * 將 [RiichiDiscardPile] 轉換成 persistence DTO。
 *
 * @return 轉換後的日麻牌河 persistence DTO。
 */
fun RiichiDiscardPile.toPersistenceDto(): RiichiDiscardPilePersistenceDto = RiichiDiscardPilePersistenceDto(
    entries.map { RiichiDiscardEntryPersistenceDto(it.tile.toPersistenceDto(), it.isRiichi, it.isTaken) },
)

/**
 * 將日麻牌河 persistence DTO 還原成 [RiichiDiscardPile]。
 *
 * @return 還原後的日麻牌河。
 */
fun RiichiDiscardPilePersistenceDto.toDomain(): RiichiDiscardPile = entries.fold(RiichiDiscardPile()) { pile, entry ->
    pile.discard(RiichiDiscardEntry(entry.tile.toDomain(), entry.isRiichi, entry.isTaken))
}
