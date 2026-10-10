package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile

/**
 * 將 [TaiwanDiscardPile] 轉換成 persistence DTO。
 *
 * @return 轉換後的台麻牌河 persistence DTO。
 */
fun TaiwanDiscardPile.toPersistenceDto(): TaiwanDiscardPilePersistenceDto = TaiwanDiscardPilePersistenceDto(
    entries.map { DiscardEntryPersistenceDto(it.tile.toPersistenceDto(), it.isTaken) },
)

/**
 * 將台麻牌河 persistence DTO 還原成 [TaiwanDiscardPile]。
 *
 * @return 還原後的台麻牌河。
 */
fun TaiwanDiscardPilePersistenceDto.toDomain(): TaiwanDiscardPile = entries.fold(TaiwanDiscardPile()) { pile, entry ->
    pile.discard(DiscardPile.DiscardEntry(entry.tile.toDomain(), entry.isTaken))
}
