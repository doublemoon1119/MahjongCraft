package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_RIICHI_TILE_ASSET_KEYS
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

/**
 * 麻將牌 item：單一 item 類型代表所有牌面，實際牌面由 NBT 的 [NBT_KEY_TILE] 字串決定
 * （對應 [com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_RIICHI_TILE_ASSET_KEYS]）。
 *
 * 右鍵（非 sneak）在空氣中循環切換到下一個牌面，供這個子項驗證材質切換用；sneak+右鍵放置成
 * 世界中的實體屬於之後 3D 渲染子項的範圍，這裡不處理。
 */
class MahjongTileItem(settings: Settings) : Item(settings) {

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (!world.isClient) {
            val nbt = stack.orCreateNbt
            val currentIndex = ALL_RIICHI_TILE_ASSET_KEYS.indexOf(nbt.getString(NBT_KEY_TILE))
            val nextKey = ALL_RIICHI_TILE_ASSET_KEYS[(currentIndex + 1) % ALL_RIICHI_TILE_ASSET_KEYS.size]
            nbt.putString(NBT_KEY_TILE, nextKey)
        }
        return TypedActionResult.success(stack, world.isClient)
    }

    companion object {
        const val NBT_KEY_TILE = "tile"
    }
}
