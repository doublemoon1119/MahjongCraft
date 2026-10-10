package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction

/**
 * 將 [ExtensionGameAction] 轉換成已註冊的網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun ExtensionGameAction.toDto(registries: NetworkDtoRegistries): ExtensionGameActionDto = registries.extensionGameAction.toDto(this)

/**
 * 將已註冊的 [ExtensionGameActionDto] 還原成領域遊戲動作。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun ExtensionGameActionDto.toDomain(registries: NetworkDtoRegistries): ExtensionGameAction = registries.extensionGameAction.toDomain(this)
