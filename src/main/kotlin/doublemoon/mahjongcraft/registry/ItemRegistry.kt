package doublemoon.mahjongcraft.registry

//名稱重複, 另寫為 RiichiMahjongTile

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.id
import doublemoon.mahjongcraft.item.Dice
import doublemoon.mahjongcraft.item.MahjongScoringStick
import doublemoon.mahjongcraft.item.MahjongTile
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.text.Text


//參考: https://fabricmc.net/wiki/tutorial:items_docs
object ItemRegistry {

    val dice: Dice = Dice(Item.Settings())
    val mahjongTile: MahjongTile = MahjongTile(
        Item.Settings().component(
            DataComponentTypes.CUSTOM_DATA,
            NbtComponent.of(NbtCompound().also { it.putInt("code", 0) })
        )
    )
    val mahjongScoringStick: MahjongScoringStick = MahjongScoringStick(
        Item.Settings().component(
            DataComponentTypes.CUSTOM_DATA,
            NbtComponent.of(NbtCompound().also { it.putInt("code", 0) })
        )
    )
    //考慮加入牌尺

    private val itemGroup = RegistryKey.of(RegistryKeys.ITEM_GROUP, id("group"))

    fun register() {
        // Items
        Registry.register(Registries.ITEM, id("dice"), dice)
        Registry.register(Registries.ITEM, id("mahjong_tile"), mahjongTile)
        Registry.register(Registries.ITEM, id("mahjong_scoring_stick"), mahjongScoringStick)

        // BlockItems
        val mahjongStoolBlockItem = BlockItem(BlockRegistry.mahjongStool, Item.Settings())
        val mahjongTableBlockItem = BlockItem(BlockRegistry.mahjongTable, Item.Settings())
        Registry.register(Registries.ITEM, id("mahjong_stool"), mahjongStoolBlockItem)
        Registry.register(Registries.ITEM, id("mahjong_table"), mahjongTableBlockItem)

        // ItemGroup
        Registry.register(Registries.ITEM_GROUP, itemGroup, FabricItemGroup.builder()
            .displayName(Text.translatable("itemGroup.$MOD_ID.group"))
            .icon { mahjongTile.defaultStack }
            .entries { _, entries ->
                entries.add(dice)
                entries.add(mahjongTile)
                entries.add(mahjongScoringStick)
                entries.add(mahjongStoolBlockItem)
                entries.add(mahjongTableBlockItem)
            }.build()
        )
    }
}