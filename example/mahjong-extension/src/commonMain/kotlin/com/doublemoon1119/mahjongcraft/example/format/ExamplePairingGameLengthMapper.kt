package com.doublemoon1119.mahjongcraft.example.format

import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingGameLengthDto
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingGameLength

/**
 * 將範例局數設定轉成網路資料。
 *
 * @return 對應的網路 DTO。
 */
fun ExamplePairingGameLength.toNetworkDto(): ExamplePairingGameLengthDto = ExamplePairingGameLengthDto(
    totalRounds = totalRounds,
)

/**
 * 還原範例局數設定。
 *
 * @return 對應的規則資料。
 */
fun ExamplePairingGameLengthDto.toDomain(): ExamplePairingGameLength = ExamplePairingGameLength(
    totalRounds = totalRounds,
)
