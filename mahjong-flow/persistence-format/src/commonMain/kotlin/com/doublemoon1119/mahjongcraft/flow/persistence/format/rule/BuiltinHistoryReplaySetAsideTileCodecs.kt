package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 內建玩家規則狀態中移出手牌的牌的投影 codec。 */
object BuiltinHistoryReplaySetAsideTileCodecs {
    /** 日麻玩家狀態中拔出的北。 */
    val riichi = HistoryReplayProjectionCodec { value, context ->
        val tiles = (value as? JsonObject ?: error("Riichi player state projection must be an object"))["nukiDoraTiles"]
            ?: error("Riichi player state projection must list pulled north tiles")
        (tiles as? JsonArray ?: error("Pulled north tiles must be an array")).map { tile ->
            context.charge()
            context.tile(tile.jsonPrimitive.takeUnless { it.isString }?.intOrNull ?: error("Pulled north tile must be a tile index"))
        }
    }
}
