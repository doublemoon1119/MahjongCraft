@file:OptIn(MahjongCraftInternalApi::class)

package com.doublemoon1119.mahjongcraft.platform.fabric.api.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.MahjongCraftInternalApi
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableControllerState
import net.minecraft.nbt.NbtCompound
import kotlin.uuid.Uuid

/** 穩定桌子 UUID 的 NBT 欄位名稱。 */
private const val NBT_KEY_TABLE_ID: String = "TableId"

/** 整桌呈現忙碌期限的 NBT 欄位名稱。 */
private const val NBT_KEY_PRESENTATION_BUSY_UNTIL_GAME_TIME: String = "PresentationBusyUntilGameTime"

/** 中途胡牌呈現忙碌期限的 NBT 欄位名稱。 */
private const val NBT_KEY_CONTINUING_WIN_PRESENTATION_BUSY_UNTIL_GAME_TIME: String = "ContinuingWinPresentationBusyUntilGameTime"

/**
 * 從方塊實體 NBT 還原麻將桌狀態；桌子 ID 損壞或缺失時保留目前的 ID。方塊實體在自己的 `readNbt` 中呼叫。
 *
 * @param nbt 方塊實體讀檔時取得的 NBT。
 */
fun MahjongTableControllerState.readNbt(nbt: NbtCompound) {
    restore(
        tableId = nbt.getString(NBT_KEY_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() },
        presentationBusyUntilGameTime = nbt.getLong(NBT_KEY_PRESENTATION_BUSY_UNTIL_GAME_TIME),
        continuingWinPresentationBusyUntilGameTime = nbt.getLong(NBT_KEY_CONTINUING_WIN_PRESENTATION_BUSY_UNTIL_GAME_TIME),
    )
}

/**
 * 把麻將桌狀態寫入方塊實體 NBT；欄位名稱與格式由 MahjongCraft 決定。方塊實體在自己的 `writeNbt` 中呼叫。
 *
 * @param nbt 方塊實體存檔時接收欄位的 NBT。
 */
fun MahjongTableControllerState.writeNbt(nbt: NbtCompound) {
    nbt.putString(NBT_KEY_TABLE_ID, tableId.toString())
    nbt.putLong(NBT_KEY_PRESENTATION_BUSY_UNTIL_GAME_TIME, presentationBusyUntilGameTime)
    nbt.putLong(NBT_KEY_CONTINUING_WIN_PRESENTATION_BUSY_UNTIL_GAME_TIME, continuingWinPresentationBusyUntilGameTime)
}
