package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftItemGroupKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_RIICHI_TILE_ASSET_KEYS
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/** MahjongCraft Fabric 創造模式物品分類的集中註冊點。 */
object ModItemGroups {
    /** 註冊以木製麻將桌為圖示、收納目前可取得麻將桌的主要物品分類。 */
    fun register() {
        val group = FabricItemGroup.builder()
            .icon { ItemStack(ModBlocks.woodenMahjongTable) }
            .displayName(Text.translatable(MinecraftItemGroupKeys.MAIN))
            .entries { _, entries ->
                entries.add(ModBlocks.woodenMahjongTable)
                entries.add(ModBlocks.modernMahjongTable)
                entries.add(
                    ItemStack(ModItems.MAHJONG_TILE).also {
                        MahjongTileItem.writeTileAssetKey(it, ALL_RIICHI_TILE_ASSET_KEYS.first())
                    },
                )
            }
            .build()
        Registry.register(
            Registries.ITEM_GROUP,
            Identifier(MinecraftModMetadata.MOD_ID, "main"),
            group,
        )
    }
}
