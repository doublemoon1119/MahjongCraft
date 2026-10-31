package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceConverter
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile.DiscardEntry

/**
 * 保存一筆捨牌及屬於這次捨牌的組件。
 *
 * @param registry 組件保存轉換。
 * @return 完整的捨牌紀錄。
 */
fun DiscardEntry.toPersistenceDto(registry: ComponentPersistenceConverter): DiscardEntryPersistenceDto = DiscardEntryPersistenceDto(
    tile = tile.toPersistenceDto(registry),
    isTaken = isTaken,
    components = registry.encode(components),
)

/**
 * 還原捨牌紀錄；組件仍屬於紀錄，不會加到牌張上。
 *
 * @param registry 組件保存轉換。
 * @return 經目標種類檢查的捨牌紀錄。
 */
fun DiscardEntryPersistenceDto.toDomain(registry: ComponentPersistenceConverter): DiscardEntry = DiscardEntry(
    tile = tile.toDomain(registry),
    isTaken = isTaken,
    components = registry.decode(components),
)
