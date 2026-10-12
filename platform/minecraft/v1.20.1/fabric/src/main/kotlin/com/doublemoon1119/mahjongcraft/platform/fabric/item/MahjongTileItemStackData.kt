package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileItemData
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import net.minecraft.item.ItemStack
import org.koin.core.context.GlobalContext

/** 麻將牌 ItemStack 的牌面資料讀寫邊界。 */
object MahjongTileItemStackData {
    /** 物品牌面欄位名稱。 */
    const val NBT_KEY_TILE: String = MahjongTileItemData.TILE_KEY

    /**
     * 缺少牌面時使用配方預設牌；無效名稱交由牌面 registry 回退。
     *
     * @param stack 要讀取的麻將牌物品堆疊。
     * @return 正規化後的牌面 asset key。
     */
    fun read(stack: ItemStack): String {
        val storedKey = stack.nbt?.takeIf { it.contains(NBT_KEY_TILE) }?.getString(NBT_KEY_TILE)
        return MahjongTileItemData.read(storedKey, GlobalContext.get().get<MinecraftTileAssetRegistry>())
    }

    /**
     * 以正規化名稱寫入牌面。
     *
     * @param stack 要更新的麻將牌物品堆疊。
     * @param assetKey 要保存的牌面 key；會由 registry 正規化。
     */
    fun write(stack: ItemStack, assetKey: String) {
        stack.orCreateNbt.putString(NBT_KEY_TILE, MahjongTileItemData.write(assetKey, GlobalContext.get().get<MinecraftTileAssetRegistry>()))
    }
}
