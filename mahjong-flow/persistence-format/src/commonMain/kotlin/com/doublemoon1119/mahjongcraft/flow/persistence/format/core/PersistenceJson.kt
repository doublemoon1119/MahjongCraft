package com.doublemoon1119.mahjongcraft.flow.persistence.format.core

import kotlinx.serialization.json.Json

/**
 * 所有存檔格式共用的 JSON 設定。
 *
 * 權威狀態、牌譜事件與 Replay 都以這份設定編解碼；改動任何設定都會改變已寫入資料的格式。這份設定與網路訊息使用的 JSON 各自獨立，
 * 調整網路格式不會影響存檔。
 */
val persistenceJson: Json = Json
