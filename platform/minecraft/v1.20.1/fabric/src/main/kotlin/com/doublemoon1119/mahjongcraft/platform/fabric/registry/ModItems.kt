package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongDiceItem
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongScoringStickItem
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModItems {
    /** 可放置自由測試骰子的麻將骰子 item。 */
    val MAHJONG_DICE: Item = MahjongDiceItem(Item.Settings())

    /** 單一物品類型表示所有麻將牌面的 item。 */
    val MAHJONG_TILE: Item = MahjongTileItem(Item.Settings())

    /** 單一物品類型表示所有點棒面額的 item。 */
    val MAHJONG_SCORING_STICK: Item = MahjongScoringStickItem(Item.Settings())

    /** 註冊 MahjongCraft 的一般 item。 */
    fun register() {
        Registry.register(Registries.ITEM, Identifier(MinecraftModMetadata.MOD_ID, "mahjong_dice"), MAHJONG_DICE)
        Registry.register(Registries.ITEM, Identifier(MinecraftModMetadata.MOD_ID, "mahjong_tile"), MAHJONG_TILE)
        Registry.register(
            Registries.ITEM,
            Identifier(MinecraftModMetadata.MOD_ID, "mahjong_scoring_stick"),
            MAHJONG_SCORING_STICK,
        )
    }
}
