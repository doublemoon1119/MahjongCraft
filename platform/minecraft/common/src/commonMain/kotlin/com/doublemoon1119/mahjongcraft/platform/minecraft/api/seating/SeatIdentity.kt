package com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 本次世界中的實際座位識別，不以提供者 ID 取代，也不保存為長期資料。
 *
 * 方塊使用 minecraft:block 與 x,y,z；實體使用 minecraft:entity 與 UUID。
 * 不同提供者宣稱同一目標時必須使用一致識別，實體不可使用 runtime ID。四個欄位都相同時視為同一個座位。
 *
 * @property dimensionId 維度 namespaced ID。
 * @property kind 來源種類 namespaced ID。
 * @property key 方塊座標、實體 UUID 或自訂穩定 key。
 * @property slot 子座位 key，單座位預設 main。
 */
class SeatIdentity(
    val dimensionId: String,
    val kind: String,
    val key: String,
    val slot: String = "main",
) {
    init {
        NamespacedId.requireValid(dimensionId) { "Invalid seat dimension ID: $dimensionId" }
        NamespacedId.requireValid(kind) { "Invalid seat kind: $kind" }
        require(key.isNotBlank()) { "Seat identity key must not be blank" }
        require(slot.isNotBlank()) { "Seat slot must not be blank" }
    }

    override fun equals(other: Any?): Boolean = this === other ||
        other is SeatIdentity &&
        dimensionId == other.dimensionId &&
        kind == other.kind &&
        key == other.key &&
        slot == other.slot

    override fun hashCode(): Int {
        var result = dimensionId.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + key.hashCode()
        return 31 * result + slot.hashCode()
    }

    override fun toString(): String = "SeatIdentity(dimensionId=$dimensionId, kind=$kind, key=$key, slot=$slot)"
}
