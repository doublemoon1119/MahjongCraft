package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason

/**
 * 將 [ExhaustiveDrawReason] 轉換成已註冊的網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun ExhaustiveDrawReason.toDto(registries: NetworkDtoRegistries): ExhaustiveDrawReasonDto = registries.exhaustiveDrawReason.toDto(this)

/**
 * 將已註冊的 [ExhaustiveDrawReasonDto] 還原成領域流局原因。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun ExhaustiveDrawReasonDto.toDomain(registries: NetworkDtoRegistries): ExhaustiveDrawReason = registries.exhaustiveDrawReason.toDomain(this)
