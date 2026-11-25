package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla

import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootTable

/**
 * 方塊宣告了會掉落物品，伺服器卻沒有載入它的戰利品表。
 *
 * @param server 目前伺服器。
 * @param block 要檢查的方塊。
 * @return 宣告了戰利品表但沒有載入時為 true；方塊本來就不掉落時為 false。
 */
fun isBlockLootTableMissing(server: MinecraftServer, block: Block): Boolean {
    val table = block.lootTable
    // 找不到時原版回傳空的戰利品表。
    return table != BuiltInLootTables.EMPTY && server.reloadableRegistries().getLootTable(table) === LootTable.EMPTY
}
