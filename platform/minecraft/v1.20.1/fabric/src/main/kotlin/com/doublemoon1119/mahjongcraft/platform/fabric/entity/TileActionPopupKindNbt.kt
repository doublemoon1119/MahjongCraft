package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement

/** 短暫牌面提示以名稱保存。 */
internal object TileActionPopupKindNbt {
    /** 寫入不受 enum 宣告順序影響的名稱。 */
    fun write(nbt: NbtCompound, key: String, kind: TileActionPopupKind) {
        nbt.putString(key, kind.name)
    }

    /** 未知名稱或型別錯誤時不顯示短暫提示。 */
    fun read(nbt: NbtCompound, key: String): TileActionPopupKind = when {
        nbt.contains(key, NbtElement.STRING_TYPE.toInt()) ->
            TileActionPopupKind.entries.firstOrNull { it.name == nbt.getString(key) } ?: TileActionPopupKind.NONE

        else -> TileActionPopupKind.NONE
    }
}
