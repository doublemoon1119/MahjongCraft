package com.doublemoon1119.mahjongcraft.platform.fabric.block.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModBlocks
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos
import kotlin.uuid.Uuid

/** 在 Minecraft 世界存檔中保存麻將桌穩定 UUID 的最小方塊實體。 */
class MahjongTableBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(ModBlocks.mahjongTableBlockEntity, pos, state) {
    /** 同時作為等待階段 `Room.id` 與開局後 `TableState.id` 的穩定識別碼。 */
    var tableId: Uuid = Uuid.random()
        private set

    /** 從方塊實體 NBT 還原穩定 UUID；損壞或缺失時保留新生成的 UUID。 */
    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        nbt.getString(NBT_KEY_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
            ?.let { restored -> tableId = restored }
    }

    /** 把穩定 UUID 寫入方塊實體 NBT。 */
    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        nbt.putString(NBT_KEY_TABLE_ID, tableId.toString())
    }

    /** 麻將桌方塊實體 NBT 欄位名稱。 */
    private companion object {
        const val NBT_KEY_TABLE_ID: String = "TableId"
    }
}
