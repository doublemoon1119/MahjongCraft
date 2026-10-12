package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement

/** 短暫牌面提示以名稱保存。 */
internal object TileActionPopupKindNbt {
    /**
     * 寫入不受 enum 宣告順序影響的名稱。
     *
     * @param nbt 目的 NBT compound。
     * @param key 欄位名稱。
     * @param kind 要保存的提示種類。
     */
    fun write(nbt: NbtCompound, key: String, kind: TileActionPopupKind) {
        nbt.putString(key, kind.name)
    }

    /**
     * 未知名稱或型別錯誤時不顯示短暫提示。
     *
     * @param nbt 含提示種類欄位的 NBT compound。
     * @param key 欄位名稱。
     * @return 欄位中的有效提示種類；欄位缺少、型別錯誤或名稱未知時為 [TileActionPopupKind.NONE]。
     */
    fun read(nbt: NbtCompound, key: String): TileActionPopupKind = when {
        nbt.contains(key, NbtElement.STRING_TYPE.toInt()) -> TileActionPopupKind.fromNameOrDefault(nbt.getString(key))

        else -> TileActionPopupKind.NONE
    }
}
