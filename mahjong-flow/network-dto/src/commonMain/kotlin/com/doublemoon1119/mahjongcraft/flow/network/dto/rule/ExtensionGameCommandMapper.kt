package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand

/**
 * 將 [ExtensionGameCommand] 轉換成已註冊的網路 DTO。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun ExtensionGameCommand.toDto(registries: NetworkDtoRegistries): ExtensionGameCommandDto = registries.extensionGameCommand.toDto(this)

/**
 * 將已註冊的 [ExtensionGameCommandDto] 還原成領域遊戲命令。
 *
 * @param registries 網路 DTO 的動態註冊表集合。
 * @return 轉換後的資料。
 */
fun ExtensionGameCommandDto.toDomain(registries: NetworkDtoRegistries): ExtensionGameCommand = registries.extensionGameCommand.toDomain(this)
