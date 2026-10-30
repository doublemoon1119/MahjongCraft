package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile

/**
 * 保存日麻牌河，保留每次捨牌的紀錄組件。
 *
 * @param registry 組件保存轉換。
 * @return 依捨牌順序排列的保存資料。
 */
fun RiichiDiscardPile.toPersistenceDto(registry: ComponentPersistenceRegistry): RiichiDiscardPilePersistenceDto = RiichiDiscardPilePersistenceDto(
    entries.map { it.toPersistenceDto(registry) },
)

/**
 * 還原日麻牌河及其紀錄組件。
 *
 * @param registry 組件保存轉換。
 * @return 還原的牌河。
 */
fun RiichiDiscardPilePersistenceDto.toDomain(registry: ComponentPersistenceRegistry): RiichiDiscardPile = entries.fold(RiichiDiscardPile()) { pile, entry ->
    pile.discard(entry.toDomain(registry))
}
