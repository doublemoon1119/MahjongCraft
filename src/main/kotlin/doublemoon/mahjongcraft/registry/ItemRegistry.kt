package doublemoon.mahjongcraft.registry

import doublemoon.mahjongcraft.id
import doublemoon.mahjongcraft.item.Dice
import doublemoon.mahjongcraft.item.MahjongScoringStick
import doublemoon.mahjongcraft.item.MahjongTile
import doublemoon.mahjongcraft.itemGroup
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.minecraft.util.registry.Registry

//參考: https://fabricmc.net/wiki/tutorial:items_docs
object ItemRegistry {

    val dice: Dice = Dice(FabricItemSettings().group(itemGroup))
    val mahjongTile: MahjongTile = MahjongTile(FabricItemSettings().group(itemGroup))
    val mahjongScoringStick: MahjongScoringStick = MahjongScoringStick(FabricItemSettings().group(itemGroup))
    //考慮加入牌尺

    fun register() {
        Registry.register(Registry.ITEM, id("dice"), dice)
        Registry.register(Registry.ITEM, id("mahjong_tile"), mahjongTile)
        Registry.register(Registry.ITEM, id("mahjong_scoring_stick"), mahjongScoringStick)
    }
}