package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.NbtCompound

/**
 * [MahjongTileEntity.nonBlockingPresentationUntilGameTime]（「動畫不阻塞全桌」豁免的絕對到期 game
 * time）在 entity NBT 中的讀寫。
 *
 * 這個欄位必須跟著世界存檔往返：牌的動畫佇列本身就是持久化的，若豁免沒有一起存下來，重啟後還沒播完
 * 的中途胡牌收尾動畫就會突然開始阻塞全桌。抽成獨立物件的唯一理由是**可測試性**——實體化一個
 * [MahjongTileEntity] 需要註冊表裡的 `EntityType` 與一個 `World`，本專案沒有 Minecraft bootstrap 的
 * 測試基礎建設，因此讀寫規則本身抽出來才能被直接覆蓋（見 `NonBlockingPresentationLeaseCodecTest`）。
 */
internal object NonBlockingPresentationLeaseCodec {
    /** NBT key；沒有這個欄位的舊存檔讀回 [NO_LEASE]。 */
    const val NBT_KEY: String = "NonBlockingPresentationUntilGameTime"

    /** 沒有豁免時的欄位值。 */
    const val NO_LEASE: Long = 0L

    /** 把豁免到期時間寫進 [nbt]。 */
    fun write(nbt: NbtCompound, untilGameTime: Long) {
        nbt.putLong(NBT_KEY, untilGameTime)
    }

    /** 從 [nbt] 讀回豁免到期時間；欄位不存在或型別不符時回傳 [NO_LEASE]。 */
    fun read(nbt: NbtCompound): Long = nbt.getLong(NBT_KEY)
}
