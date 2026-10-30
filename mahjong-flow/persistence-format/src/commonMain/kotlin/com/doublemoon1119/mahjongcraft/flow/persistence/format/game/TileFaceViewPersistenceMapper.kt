package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentViewPersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView

/**
 * 保存已裁切的牌面與特徵，不重新執行規則投影。
 *
 * @param registry 已登記的投影保存轉換。
 * @return 沒有局內身分或原始組件的保存資料。
 */
fun TileFaceView.toPersistenceDto(registry: ComponentViewPersistenceRegistry): TileFaceViewPersistenceDto = TileFaceViewPersistenceDto(
    tile = tile.toPersistenceDto(),
    views = registry.encode(views),
    appearanceId = appearanceId,
)

/**
 * 還原當時已保存的牌面特徵，不建立權威組件或重算目前規則。
 *
 * @param registry 已登記的投影保存轉換。
 * @return 提交時保存的牌面與特徵。
 */
fun TileFaceViewPersistenceDto.toDomain(registry: ComponentViewPersistenceRegistry): TileFaceView = TileFaceView(
    tile = tile.toDomain(),
    views = registry.decode(views),
    appearanceId = appearanceId,
)
