package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayDiscard
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiHistoryDiscardMarkerIds
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionCodec
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** 內建牌河投影 codec。 */
object BuiltinHistoryReplayDiscardCodecs {
    /** Riichi 牌河 codec。 */
    val riichi = HistoryReplayProjectionCodec<List<HistoryReplayDiscard>> { value, context -> decode(value, context, true) }

    /** Taiwan 牌河 codec。 */
    val taiwan = HistoryReplayProjectionCodec<List<HistoryReplayDiscard>> { value, context -> decode(value, context, false) }

    /** 轉換內建規則牌河的索引與公開標記，不恢復牌 UUID。
     * @param value 內部牌河 payload。
     * @param context 局內索引與預算。
     * @param riichi 是否讀取日麻專屬宣告標記。
     * @return 保留已被鳴走紀錄的牌河。
     */
    private fun decode(value: JsonElement, context: HistoryReplayProjectionContext, riichi: Boolean): List<HistoryReplayDiscard> {
        val entries = (value as? JsonObject)?.get("entries")?.jsonArray ?: error("Discard projection lacks entries")
        return entries.map { entry ->
            val objectValue = entry as? JsonObject ?: error("Discard entry must be an object")
            val tile = objectValue["tile"]?.jsonPrimitive?.takeUnless { it.isString }?.intOrNull ?: error("Discard entry lacks tile")
            context.charge()
            val taken = objectValue["isTaken"]?.jsonPrimitive?.takeUnless { it.isString }?.booleanOrNull ?: error("Discard entry lacks taken flag")
            val declared = if (riichi) objectValue["isRiichi"]?.jsonPrimitive?.takeUnless { it.isString }?.booleanOrNull ?: error("Discard entry lacks declaration flag") else false
            HistoryReplayDiscard(context.tile(tile), taken, if (declared) setOf(RiichiHistoryDiscardMarkerIds.RIICHI_DECLARED) else emptySet())
        }
    }
}
