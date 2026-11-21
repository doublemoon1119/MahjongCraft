package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

/** 短暫牌面提示以名稱保存。 */
internal object TileActionPopupKindNbt {
    /**
     * 寫入不受 enum 宣告順序影響的名稱。
     *
     * @param nbt 目的 NBT compound。
     * @param key 欄位名稱。
     * @param kind 要保存的提示種類。
     */
    fun write(nbt: CompoundTag, key: String, kind: TileActionPopupKind) {
        nbt.putString(key, kind.name)
    }

    /**
     * 未知名稱或型別錯誤時不顯示短暫提示。
     *
     * @param nbt 含提示種類欄位的 NBT compound。
     * @param key 欄位名稱。
     * @return 欄位中的有效提示種類；欄位缺少、型別錯誤或名稱未知時為 [TileActionPopupKind.NONE]。
     */
    fun read(nbt: CompoundTag, key: String): TileActionPopupKind = when {
        nbt.contains(key, Tag.TAG_STRING.toInt()) -> TileActionPopupKind.fromNameOrDefault(nbt.getString(key))

        else -> TileActionPopupKind.NONE
    }
}
