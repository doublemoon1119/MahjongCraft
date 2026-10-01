package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 精簡 Replay 解碼後的單筆交易內容。
 *
 * @property projection 交易結束後可查閱的遊戲內容投影。
 * @property facts 交易中的有序語意事實；牌與玩家參照使用文件內的索引。
 * @property actorSeats 與語意事實對應的玩家初始座位索引；系統事件為 null。
 * @property newTileTypes 交易期間新宣告的牌種索引。
 */
data class DecodedReplayTransaction(
    val projection: JsonElement,
    val facts: List<JsonObject>,
    val actorSeats: List<Int?>,
    val newTileTypes: List<Int>,
)
