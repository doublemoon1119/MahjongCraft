package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

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
    fun read(nbt: CompoundTag): TablePropIdentity? {
        if (!nbt.contains(NBT_KEY_PLACEMENT_ID, Tag.TAG_STRING.toInt()) ||
            !nbt.contains(NBT_KEY_RULE_MODULE_ID, Tag.TAG_STRING.toInt())
        ) {
            return null
        }
        val placementId = nbt.getString(NBT_KEY_PLACEMENT_ID)
        val ruleModuleId = nbt.getString(NBT_KEY_RULE_MODULE_ID)
        if (!MinecraftResourceIds.isValid(placementId) || !MinecraftResourceIds.isValid(ruleModuleId)) return null
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
        nbt: CompoundTag,
        placementId: String,
        ruleModuleId: String,
    ) {
        if (!MinecraftResourceIds.isValid(placementId) || !MinecraftResourceIds.isValid(ruleModuleId)) {
            nbt.remove(NBT_KEY_PLACEMENT_ID)
            nbt.remove(NBT_KEY_RULE_MODULE_ID)
            return
        }
        nbt.putString(NBT_KEY_PLACEMENT_ID, placementId)
        nbt.putString(NBT_KEY_RULE_MODULE_ID, ruleModuleId)
    }
}
