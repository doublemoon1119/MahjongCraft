package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * 集中計算 JSON 線路 UTF-8 容量，避免不同查詢族群使用不一致的字元組大小語意。
 *
 * @param T 線路回應型別。
 * @param json 線路 JSON 編碼設定。
 * @param serializer 回應型別 serializer。
 * @param value 欲計算的回應。
 * @return 編碼後 UTF-8 位元組數。
 */
internal fun <T> encodedHistoryResponseUtf8Size(json: Json, serializer: KSerializer<T>, value: T): Int = json.encodeToString(serializer, value).encodeToByteArray().size
