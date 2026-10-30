package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.NbtCompound

/** 常駐資訊面板隱藏期限的 NBT 讀寫；兩種面板共用相同保存欄位。 */
object TableOverlayVisibilityNbt {
    /**
     * 讀取隱藏期限。
     *
     * @param nbt 面板的保存資料。
     * @return 正的截止時間；缺少或無效時為 Long.MIN_VALUE，表示未設定隱藏。
     */
    fun read(nbt: NbtCompound): Long = nbt.getLong(NBT_KEY_HIDDEN_UNTIL).takeIf { it > 0L } ?: Long.MIN_VALUE

    /**
     * 保存隱藏期限，沒有隱藏設定時移除舊欄位。
     *
     * @param nbt 面板的保存資料。
     * @param hiddenUntilGameTime 截止時間；Long.MIN_VALUE 表示沒有隱藏設定。
     */
    fun write(nbt: NbtCompound, hiddenUntilGameTime: Long) {
        if (hiddenUntilGameTime == Long.MIN_VALUE) {
            nbt.remove(NBT_KEY_HIDDEN_UNTIL)
        } else {
            nbt.putLong(NBT_KEY_HIDDEN_UNTIL, hiddenUntilGameTime)
        }
    }

    /** 兩種面板共用的隱藏期限保存鍵。 */
    private const val NBT_KEY_HIDDEN_UNTIL = "HiddenUntilGameTime"
}
