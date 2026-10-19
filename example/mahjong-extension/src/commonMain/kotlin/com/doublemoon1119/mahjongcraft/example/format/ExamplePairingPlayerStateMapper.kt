package com.doublemoon1119.mahjongcraft.example.format

import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingPlayerStateDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingPlayerStatePersistenceDto
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingPlayerState

/**
 * 將公開宣告標記轉成網路資料。
 *
 * @return 玩家的網路狀態。
 */
fun ExamplePairingPlayerState.toNetworkDto(): ExamplePairingPlayerStateDto = ExamplePairingPlayerStateDto(declared)

/**
 * 還原公開宣告標記。
 *
 * @return 玩家規則狀態。
 */
fun ExamplePairingPlayerStateDto.toDomain(): ExamplePairingPlayerState = ExamplePairingPlayerState(declared)

/**
 * 將公開宣告標記轉成保存資料。
 *
 * @return 玩家的保存狀態。
 */
fun ExamplePairingPlayerState.toPersistenceDto(): ExamplePairingPlayerStatePersistenceDto = ExamplePairingPlayerStatePersistenceDto(declared)

/**
 * 還原已保存的公開宣告標記。
 *
 * @return 玩家規則狀態。
 */
fun ExamplePairingPlayerStatePersistenceDto.toDomain(): ExamplePairingPlayerState = ExamplePairingPlayerState(declared)
