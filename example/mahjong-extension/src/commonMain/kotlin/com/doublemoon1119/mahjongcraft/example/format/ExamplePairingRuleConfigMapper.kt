package com.doublemoon1119.mahjongcraft.example.format

import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingRuleConfigDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingRuleConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig

/**
 * 將簡易配對設定轉成網路資料。
 *
 * @return 可傳送的規則設定。
 */
fun ExamplePairingRuleConfig.toNetworkDto(): ExamplePairingRuleConfigDto = ExamplePairingRuleConfigDto(
    exchangeEnabled = exchangeEnabled,
    paymentPerPlayer = paymentPerPlayer,
    roundCount = roundCount,
)

/**
 * 還原並驗證簡易配對網路設定。
 *
 * @return 通過規則建構驗證的設定。
 */
fun ExamplePairingRuleConfigDto.toDomain(): ExamplePairingRuleConfig = ExamplePairingRuleConfig(
    exchangeEnabled = exchangeEnabled,
    paymentPerPlayer = paymentPerPlayer,
    roundCount = roundCount,
)

/**
 * 將簡易配對設定轉成獨立的保存資料。
 *
 * @return 可保存的規則設定。
 */
fun ExamplePairingRuleConfig.toPersistenceDto(): ExamplePairingRuleConfigPersistenceDto = ExamplePairingRuleConfigPersistenceDto(
    exchangeEnabled = exchangeEnabled,
    paymentPerPlayer = paymentPerPlayer,
    roundCount = roundCount,
)

/**
 * 還原並驗證已保存的簡易配對設定。
 *
 * @return 通過規則建構驗證的設定。
 */
fun ExamplePairingRuleConfigPersistenceDto.toDomain(): ExamplePairingRuleConfig = ExamplePairingRuleConfig(
    exchangeEnabled = exchangeEnabled,
    paymentPerPlayer = paymentPerPlayer,
    roundCount = roundCount,
)
