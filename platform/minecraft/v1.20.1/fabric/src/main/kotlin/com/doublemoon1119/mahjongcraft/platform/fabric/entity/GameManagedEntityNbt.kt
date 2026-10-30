package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import net.minecraft.nbt.NbtCompound
import kotlin.uuid.Uuid

/**
 * 牌局管理狀態的 NBT 讀寫工具。
 *
 * 牌局管理旗標和桌子 UUID 分開保存；旗標為 `true` 時，即使 UUID 損壞也不能退回自由放置模式。
 */
object GameManagedEntityNbt {
    /**
     * 從 NBT 讀取牌局管理狀態。
     *
     * @param nbt 含牌局管理欄位的 NBT compound。
     * @return 讀取到的牌局管理狀態。
     */
    fun read(nbt: NbtCompound): GameManagedEntityState = GameManagedEntityState(
        managedByGame = nbt.getBoolean(NBT_KEY_MANAGED_BY_GAME),
        tableId = ManagedTableNbt.read(nbt),
    )

    /**
     * 寫入牌局管理狀態與桌子 UUID。
     *
     * @param nbt 寫入目標 NBT compound。
     * @param managedByGame 是否由牌局生命週期管理。
     * @param tableId 所屬桌子 UUID；沒有桌子時為 `null`。
     */
    fun write(nbt: NbtCompound, managedByGame: Boolean, tableId: Uuid?) {
        nbt.putBoolean(NBT_KEY_MANAGED_BY_GAME, managedByGame)
        ManagedTableNbt.write(nbt, tableId)
    }

    /** 區分牌局管理與自由放置模式的固定保存鍵。 */
    private const val NBT_KEY_MANAGED_BY_GAME = "ManagedByGame"
}
