package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongDiceItem
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongRulerItem
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongScoringStickItem
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item.MahjongHandbookItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerColor
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

/** MahjongCraft 一般物品的實例及登錄入口。 */
object ModItems {
    /** 可放置自由測試骰子的麻將骰子 item。 */
    val MAHJONG_DICE: Item = MahjongDiceItem(Item.Properties())

    /** 單一物品類型表示所有麻將牌面的 item。 */
    val MAHJONG_TILE: Item = MahjongTileItem(Item.Properties())

    /** 可由原版書與麻將牌合成的手冊，拿在手上按右鍵開啟麻將手冊快捷畫面。 */
    val MAHJONG_HANDBOOK: Item = MahjongHandbookItem(Item.Properties())

    /** 單一物品類型表示所有點棒面額的 item。 */
    val MAHJONG_SCORING_STICK: Item = MahjongScoringStickItem(Item.Properties())

    /** 每種顏色各一個的牌尺 item，依 [MahjongRulerColor] 的順序排列。 */
    val MAHJONG_RULERS: Map<MahjongRulerColor, Item> = MahjongRulerColor.entries.associateWith { color -> MahjongRulerItem(color, Item.Properties()) }

    /** 註冊 MahjongCraft 的一般 item。 */
    fun register() {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_dice"), MAHJONG_DICE)
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_tile"), MAHJONG_TILE)
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_handbook"), MAHJONG_HANDBOOK)
        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_scoring_stick"),
            MAHJONG_SCORING_STICK,
        )
        MAHJONG_RULERS.forEach { (color, item) ->
            Registry.register(BuiltInRegistries.ITEM, ResourceLocation(MinecraftModMetadata.MOD_ID, color.itemPath), item)
        }
    }
}
