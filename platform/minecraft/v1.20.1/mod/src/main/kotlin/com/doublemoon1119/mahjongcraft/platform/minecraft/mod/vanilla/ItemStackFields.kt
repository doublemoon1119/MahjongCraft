package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickItemData
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileItemData
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack

/**
 * 登記物品保存欄位需要的登記表項目。這個版本把欄位存在物品 NBT，不需要登記。
 *
 * @param registrar 寫入登記表的方式。
 */
@Suppress("UNUSED_PARAMETER")
fun registerItemStackFields(registrar: ModRegistrar) = Unit

/**
 * 麻將牌物品保存的牌面名稱。
 *
 * @return 保存的名稱。沒有保存或資料型別不對時為 null。
 */
fun ItemStack.storedTileAssetKey(): String? = readItemString(tag, MahjongTileItemData.TILE_KEY)

/**
 * 把牌面名稱保存到麻將牌物品。
 *
 * @param assetKey 已正規化的牌面名稱。
 */
fun ItemStack.storeTileAssetKey(assetKey: String) {
    writeItemString(orCreateTag, MahjongTileItemData.TILE_KEY, assetKey)
}

/**
 * 點棒物品保存的面額名稱。
 *
 * @return 保存的名稱。沒有保存或資料型別不對時為 null。
 */
fun ItemStack.storedScoringStickDenomination(): String? = readItemString(tag, MahjongScoringStickItemData.DENOMINATION_KEY)

/**
 * 把面額名稱保存到點棒物品。
 *
 * @param denomination 面額名稱。
 */
fun ItemStack.storeScoringStickDenomination(denomination: String) {
    writeItemString(orCreateTag, MahjongScoringStickItemData.DENOMINATION_KEY, denomination)
}

/**
 * 讀取物品 NBT 中的文字欄位。
 *
 * @param nbt 物品 NBT。沒有資料時為 null。
 * @param key 欄位名稱。
 * @return 欄位的文字。欄位不存在或不是文字時為 null。
 */
internal fun readItemString(nbt: CompoundTag?, key: String): String? = nbt?.takeIf { it.contains(key, Tag.TAG_STRING.toInt()) }?.getString(key)

/**
 * 寫入物品 NBT 中的文字欄位。
 *
 * @param nbt 物品 NBT。
 * @param key 欄位名稱。
 * @param value 文字。
 */
internal fun writeItemString(nbt: CompoundTag, key: String, value: String) {
    nbt.putString(key, value)
}
