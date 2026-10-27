package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement

/** 規則物件身分的共用 NBT 讀寫；不改動所屬桌子或其他實體欄位。 */
object TablePropIdentityNbt {
    /** 規則給定的穩定物件 ID。 */
    private const val NBT_KEY_PLACEMENT_ID = "TablePropPlacementId"

    /** 建立物件的規則模組 ID。 */
    private const val NBT_KEY_RULE_MODULE_ID = "TablePropRuleModuleId"

    /**
     * 還原完整且格式有效的規則物件身分。
     *
     * @param nbt 實體保存資料。
     * @return 兩個完整命名空間 ID；缺少、型別錯誤或內容無效時為 null，交由同步移除重建。
     */
    fun read(nbt: NbtCompound): TablePropIdentity? {
        if (!nbt.contains(NBT_KEY_PLACEMENT_ID, NbtElement.STRING_TYPE.toInt()) ||
            !nbt.contains(NBT_KEY_RULE_MODULE_ID, NbtElement.STRING_TYPE.toInt())
        ) {
            return null
        }
        val placementId = nbt.getString(NBT_KEY_PLACEMENT_ID)
        val ruleModuleId = nbt.getString(NBT_KEY_RULE_MODULE_ID)
        if (!validId(placementId) || !validId(ruleModuleId)) return null
        return TablePropIdentity(placementId, ruleModuleId)
    }

    /**
     * 保存規則物件身分；自由放置物件沒有身分時移除兩個欄位，不保存半套資料。
     *
     * @param nbt 接收資料的 NBT compound。
     * @param placementId 規則物件 ID；自由放置物件為空字串。
     * @param ruleModuleId 來源規則 ID；自由放置物件為空字串。
     */
    fun write(
        nbt: NbtCompound,
        placementId: String,
        ruleModuleId: String,
    ) {
        if (!validId(placementId) || !validId(ruleModuleId)) {
            nbt.remove(NBT_KEY_PLACEMENT_ID)
            nbt.remove(NBT_KEY_RULE_MODULE_ID)
            return
        }
        nbt.putString(NBT_KEY_PLACEMENT_ID, placementId)
        nbt.putString(NBT_KEY_RULE_MODULE_ID, ruleModuleId)
    }

    /**
     * 檢查完整命名空間 ID，不把未識別的衍生資料視為有效物件。
     *
     * @param id 待檢查的字串。
     * @return 格式有效時為 true。
     */
    private fun validId(id: String): Boolean = try {
        MinecraftResourceIds.requireValid(id) { "Invalid table prop identity: $id" }
        true
    } catch (_: IllegalArgumentException) {
        false
    }
}
