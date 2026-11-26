package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.NetworkDtoConverter
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView

/**
 * 將已裁切的牌面轉成網路資料，不讀取任何權威組件。
 *
 * @param registries 已登記的公開特徵轉換。
 * @return 不含局內 UUID 的牌面資料。
 */
fun TileFaceView.toDto(registries: NetworkDtoConverter): TileFaceViewDto = TileFaceViewDto(
    tile = tile.toDto(),
    views = registries.componentViews.encode(views),
    appearanceId = appearanceId,
)

/**
 * 還原已裁切牌面，不建立權威牌張或權威組件。
 *
 * @param registries 已登記的公開特徵轉換。
 * @return 不含局內身分的牌面。
 */
fun TileFaceViewDto.toDomain(registries: NetworkDtoConverter): TileFaceView = TileFaceView(
    tile = tile.toDomain(),
    views = registries.componentViews.decode(views),
    appearanceId = appearanceId,
)
