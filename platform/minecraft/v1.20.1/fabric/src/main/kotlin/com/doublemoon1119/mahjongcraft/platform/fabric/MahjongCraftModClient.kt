package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_RIICHI_TILE_ASSET_KEYS
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.item.ModelPredicateProviderRegistry
import net.minecraft.util.Identifier

class MahjongCraftModClient : ClientModInitializer {

    override fun onInitializeClient() {
        ModelPredicateProviderRegistry.register(ModItems.MAHJONG_TILE, Identifier("tile")) { stack, _, _, _ ->
            val storedKey = stack.nbt?.getString(MahjongTileItem.NBT_KEY_TILE)
            val index = storedKey?.let { ALL_RIICHI_TILE_ASSET_KEYS.indexOf(it) } ?: -1
            (if (index >= 0) index else 0) / 100f
        }
    }
}
