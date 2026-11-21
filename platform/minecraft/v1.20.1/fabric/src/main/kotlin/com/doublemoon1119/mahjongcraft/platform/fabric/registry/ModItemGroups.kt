package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftItemGroupKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.STANDARD_TILE_ASSET_KEYS
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/** MahjongCraft Fabric 創造模式物品分類的集中註冊點。 */
object ModItemGroups {
    /** 註冊以一萬麻將牌為圖示、收納目前可取得物品與方塊的主要物品分類。 */
    fun register() {
        val iconStack = ItemStack(ModItems.MAHJONG_TILE).also {
            MahjongTileItem.writeTileAssetKey(it, STANDARD_TILE_ASSET_KEYS.first())
        }
        val group = FabricItemGroup.builder()
            .icon(iconStack::copy)
            .title(Component.translatable(MinecraftItemGroupKeys.MAIN))
            .displayItems { _, entries ->
                entries.accept(iconStack.copy())
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
        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ResourceLocation(MinecraftModMetadata.MOD_ID, "main"),
            group,
        )
    }
}
