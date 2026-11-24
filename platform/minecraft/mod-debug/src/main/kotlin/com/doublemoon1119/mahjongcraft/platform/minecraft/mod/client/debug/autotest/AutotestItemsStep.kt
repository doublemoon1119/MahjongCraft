package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.time.Duration.Companion.seconds

/** 把本 mod 的每個物品放進快捷欄截圖，並放下各種麻將桌與凳子截圖。 */
internal object AutotestItemsStep : AutotestStep {
    override val id: String = "items"

    override suspend fun run(context: AutotestContext) {
        val itemIds = BuiltInRegistries.ITEM.keySet().filter { it.namespace == MinecraftModMetadata.MOD_ID }.map { it.toString() }.sorted()
        context.check("Mod items are registered", itemIds.isNotEmpty(), "${itemIds.size} items")
        if (!context.teleport(x = 0.5, y = -60.0, z = -3.5, yaw = 0f, pitch = 20f)) return
        val player = checkNotNull(context.client.player)
        itemIds.chunked(HOTBAR_SIZE).forEachIndexed { page, ids ->
            if (!context.clearInventory()) return
            if (!ids.all { context.runCheckedCommand("give @s $it") }) return
            val received = context.waitUntil(SYNC_TIMEOUT) { hotbarItemIds(player) == ids }
            if (!context.check("Hotbar page ${page + 1} holds its items", received, hotbarItemIds(player).joinToString())) return
            context.screenshot("hotbar-${page + 1}")
        }
        if (!context.clearInventory()) return
        if (!BLOCKS.all { (position, block) -> context.runCheckedCommand("setblock $position ${MinecraftModMetadata.MOD_ID}:$block") }) return
        val placed = context.waitUntil(SYNC_TIMEOUT) {
            BLOCKS.all { (position, block) ->
                val (x, y, z) = position.split(' ').map(String::toInt)
                val state = context.client.level?.getBlockState(BlockPos(x, y, z))
                state != null && BuiltInRegistries.BLOCK.getKey(state.block).path == block
            }
        }
        context.check("Tables and stools are placed", placed)
        context.screenshot("blocks")
    }

    /**
     * 快捷欄中非空格子的物品識別碼，依格子順序。
     *
     * @param player 本機玩家。
     * @return 物品識別碼。
     */
    private fun hotbarItemIds(player: Player): List<String> = (0 until HOTBAR_SIZE).map(player.inventory::getItem)
        .filterNot(ItemStack::isEmpty)
        .map { BuiltInRegistries.ITEM.getKey(it.item).toString() }

    /** 等用戶端收到伺服器變化的最長時間。 */
    private val SYNC_TIMEOUT = 10.seconds

    /** 快捷欄的格數。 */
    private const val HOTBAR_SIZE = 9

    /** 放下的方塊：位置與方塊名稱。 */
    private val BLOCKS = listOf(
        "-3 -60 3" to "wooden_mahjong_table",
        "3 -60 3" to "concrete_mahjong_table",
        "-1 -60 1" to "wooden_mahjong_stool",
        "1 -60 1" to "plastic_mahjong_stool",
    )
}
