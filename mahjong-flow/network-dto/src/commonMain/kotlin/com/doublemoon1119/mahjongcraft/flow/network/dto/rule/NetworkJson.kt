package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

/**
 * 網路訊息與狀態快照共用的 JSON 格式，只用於網路 DTO。
 *
 * 多型 DTO 由 [registries] 動態解析：每次編解碼都查詢同一份註冊表，因此即使這個格式先於 extension bootstrap 建立，
 * 稍後完成的 DTO 註冊仍會生效。存檔不使用這個格式，調整網路格式不會影響已寫入的資料。
 *
 * @param registries 網路訊息與擴充功能共用的 DTO 執行期間註冊表。
 */
class NetworkJson(registries: NetworkDtoRegistries) : StringFormat by Json(builderAction = { serializersModule = buildMahjongDtoSerializersModule(registries) })
