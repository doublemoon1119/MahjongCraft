package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.RegisteredEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickItemData
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileItemData
import com.mojang.serialization.Codec
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.world.item.ItemStack

/** 麻將牌物品保存牌面名稱的資料元件；[registerItemStackFields] 交出的登記完成後可讀取。 */
private lateinit var tileAssetKeyComponent: RegisteredEntry<DataComponentType<String>>

/** 點棒物品保存面額名稱的資料元件；[registerItemStackFields] 交出的登記完成後可讀取。 */
private lateinit var scoringStickDenominationComponent: RegisteredEntry<DataComponentType<String>>

/**
 * 登記物品保存欄位使用的資料元件，ID 為 `mahjongcraft:<欄位名稱>`。
 *
 * @param registrar 寫入登記表的方式。
 */
fun registerItemStackFields(registrar: ModRegistrar) {
    tileAssetKeyComponent = registrar.registerTextComponent(MahjongTileItemData.TILE_KEY)
    scoringStickDenominationComponent = registrar.registerTextComponent(MahjongScoringStickItemData.DENOMINATION_KEY)
}

/**
 * 麻將牌物品保存的牌面名稱。
 *
 * @return 保存的名稱；沒有保存時為 null。
 */
fun ItemStack.storedTileAssetKey(): String? = get(tileAssetKeyComponent.get())

/**
 * 把牌面名稱保存到麻將牌物品。
 *
 * @param assetKey 已正規化的牌面名稱。
 */
fun ItemStack.storeTileAssetKey(assetKey: String) {
    set(tileAssetKeyComponent.get(), assetKey)
}

/**
 * 點棒物品保存的面額名稱。
 *
 * @return 保存的名稱；沒有保存時為 null。
 */
fun ItemStack.storedScoringStickDenomination(): String? = get(scoringStickDenominationComponent.get())

/**
 * 把面額名稱保存到點棒物品。
 *
 * @param denomination 面額名稱。
 */
fun ItemStack.storeScoringStickDenomination(denomination: String) {
    set(scoringStickDenominationComponent.get(), denomination)
}

/**
 * 登記一個存成文字、會隨存檔保存並同步給用戶端的資料元件。
 *
 * @param path 資料元件 ID 的路徑。
 * @return 登記完成後可讀取資料元件的項目。
 */
private fun ModRegistrar.registerTextComponent(path: String): RegisteredEntry<DataComponentType<String>> = register(
    registry = Registries.DATA_COMPONENT_TYPE,
    id = resourceId(MinecraftModMetadata.MOD_ID, path),
    factory = { DataComponentType.builder<String>().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build() },
)
