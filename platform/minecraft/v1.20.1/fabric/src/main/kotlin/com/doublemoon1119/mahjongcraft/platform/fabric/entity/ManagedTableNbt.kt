package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import kotlin.uuid.Uuid

/** 實體所屬桌子 UUID 的 NBT 讀寫。 */
object ManagedTableNbt {
    /**
     * 讀取桌子 UUID。
     *
     * @param nbt 含桌子 UUID 欄位的 NBT compound。
     * @return 有效 UUID；欄位缺少、型別錯誤或內容無效時為 `null`。
     */
    fun read(nbt: NbtCompound): Uuid? {
        if (!nbt.contains(NBT_KEY_MANAGED_TABLE_ID, NbtElement.STRING_TYPE.toInt())) return null
        return try {
            Uuid.parse(nbt.getString(NBT_KEY_MANAGED_TABLE_ID))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * 寫入或清除桌子 UUID。
     *
     * @param nbt 寫入目標 NBT compound。
     * @param tableId 所屬桌子 UUID；`null` 會移除欄位。
     */
    fun write(nbt: NbtCompound, tableId: Uuid?) {
        if (tableId == null) {
            nbt.remove(NBT_KEY_MANAGED_TABLE_ID)
        } else {
            nbt.putString(NBT_KEY_MANAGED_TABLE_ID, tableId.toString())
        }
    }

    /** 所屬桌子 UUID 的固定保存鍵。 */
    private const val NBT_KEY_MANAGED_TABLE_ID = "ManagedTableId"
}
