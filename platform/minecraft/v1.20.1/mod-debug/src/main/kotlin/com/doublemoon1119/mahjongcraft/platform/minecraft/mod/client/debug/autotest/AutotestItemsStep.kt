package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries

/** 把本 mod 的每個物品放進快捷欄截圖，並放下各種麻將桌與凳子截圖。 */
internal object AutotestItemsStep : AutotestStep {
    override val id: String = "items"

    override suspend fun run(context: AutotestContext) {
        val itemIds = BuiltInRegistries.ITEM.keySet().filter { it.namespace == MinecraftModMetadata.MOD_ID }.map { it.toString() }.sorted()
        context.check("Mod items are registered", itemIds.isNotEmpty(), "${itemIds.size} items")
        context.runCommand("tp @s 0.5 -60 -3.5 0 20")
        itemIds.chunked(HOTBAR_SIZE).forEachIndexed { page, ids ->
            context.runCommand("clear @s")
            ids.forEach { context.runCommand("give @s $it") }
            context.screenshot("hotbar-${page + 1}")
        }
        context.runCommand("clear @s")
        BLOCKS.forEach { (position, block) -> context.runCommand("setblock $position ${MinecraftModMetadata.MOD_ID}:$block") }
        val placed = BLOCKS.all { (position, block) ->
            val (x, y, z) = position.split(' ').map(String::toInt)
            val state = context.client.level?.getBlockState(BlockPos(x, y, z))
            state != null && BuiltInRegistries.BLOCK.getKey(state.block).path == block
        }
        context.check("Tables and stools are placed", placed)
        context.screenshot("blocks")
    }

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
