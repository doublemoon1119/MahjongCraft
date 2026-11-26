package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceConverter
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile

/**
 * 保存台麻牌河，保留每次捨牌的紀錄組件。
 *
 * @param registry 組件保存轉換。
 * @return 依捨牌順序排列的保存資料。
 */
fun TaiwanDiscardPile.toPersistenceDto(registry: ComponentPersistenceConverter): TaiwanDiscardPilePersistenceDto = TaiwanDiscardPilePersistenceDto(
    entries = entries.map { it.toPersistenceDto(registry) },
)

/**
 * 還原台麻牌河及其紀錄組件。
 *
 * @param registry 組件保存轉換。
 * @return 還原的牌河。
 */
fun TaiwanDiscardPilePersistenceDto.toDomain(registry: ComponentPersistenceConverter): TaiwanDiscardPile = entries.fold(TaiwanDiscardPile()) { pile, entry ->
    pile.discard(entry.toDomain(registry))
}
