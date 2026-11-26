package com.doublemoon1119.mahjongcraft.example.format

import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingScoreConfigDto
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingScoreConfig

/**
 * 將範例計分設定轉成網路資料。
 *
 * @return 對應的網路 DTO。
 */
fun ExamplePairingScoreConfig.toNetworkDto(): ExamplePairingScoreConfigDto = ExamplePairingScoreConfigDto(
    initialScore = initialScore,
    bustThreshold = bustThreshold,
)

/**
 * 還原範例計分設定。
 *
 * @return 對應的規則資料。
 */
fun ExamplePairingScoreConfigDto.toDomain(): ExamplePairingScoreConfig = ExamplePairingScoreConfig(
    initialScore = initialScore,
    bustThreshold = bustThreshold,
)
