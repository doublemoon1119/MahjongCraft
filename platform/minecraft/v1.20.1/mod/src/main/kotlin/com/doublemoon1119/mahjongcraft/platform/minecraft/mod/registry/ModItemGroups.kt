package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftItemGroupKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.STANDARD_TILE_ASSET_KEYS
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/** MahjongCraft 創造模式物品分類的集中登記點。 */
object ModItemGroups {
    /**
     * 登記以一萬麻將牌為圖示、收納目前可取得物品與方塊的主要物品分類。
     *
     * @param registrar 寫入登記表的方式；物品與方塊必須已交給同一個 [registrar] 登記。
     */
    fun register(registrar: ModRegistrar) {
        registrar.register(
            registry = Registries.CREATIVE_MODE_TAB,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "main"),
        ) {
            registrar.creativeModeTabBuilder()
                .icon(::iconStack)
                .title(Component.translatable(MinecraftItemGroupKeys.MAIN))
                .displayItems { _, entries ->
                    entries.accept(iconStack())
                    entries.accept(ModItems.MAHJONG_DICE)
                    entries.accept(ModItems.MAHJONG_HANDBOOK)
                    entries.accept(ModItems.MAHJONG_SCORING_STICK)
                    ModItems.MAHJONG_RULERS.values.forEach(entries::accept)
                    entries.accept(ModBlocks.woodenMahjongTable)
                    entries.accept(ModBlocks.concreteMahjongTable)
                    entries.accept(ModBlocks.woodenMahjongStool)
                    entries.accept(ModBlocks.plasticMahjongStool)
                }
                .build()
        }
    }

    /**
     * 建立分類圖示使用的一萬麻將牌。
     *
     * @return 新的一萬麻將牌物品堆疊。
     */
    private fun iconStack(): ItemStack = ItemStack(ModItems.MAHJONG_TILE).also {
        MahjongTileItem.writeTileAssetKey(it, STANDARD_TILE_ASSET_KEYS.first())
    }
}
