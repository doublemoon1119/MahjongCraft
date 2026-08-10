package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModItems {

    val MAHJONG_TILE: Item = MahjongTileItem(Item.Settings())

    fun register() {
        Registry.register(Registries.ITEM, Identifier(MinecraftModMetadata.MOD_ID, "mahjong_tile"), MAHJONG_TILE)
    }
}
