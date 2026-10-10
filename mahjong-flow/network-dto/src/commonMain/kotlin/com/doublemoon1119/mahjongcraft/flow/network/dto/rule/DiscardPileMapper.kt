package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile

/**
 * 將 [DiscardPile] 轉換成已註冊的網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun DiscardPile<*>.toDto(registries: NetworkDtoRegistries): DiscardPileDto = registries.discardPile.toDto(this)

/**
 * 將已註冊的 [DiscardPileDto] 還原成領域牌河。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun DiscardPileDto.toDomain(registries: NetworkDtoRegistries): DiscardPile<*> = registries.discardPile.toDomain(this)
