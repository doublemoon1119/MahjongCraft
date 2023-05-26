package doublemoon.mahjongcraft.registry

//名稱重複, 另寫為 RiichiMahjongTile
import doublemoon.mahjongcraft.id
import doublemoon.mahjongcraft.item.Dice
import doublemoon.mahjongcraft.item.MahjongScoringStick
import doublemoon.mahjongcraft.item.MahjongTile
import doublemoon.mahjongcraft.itemGroup
import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry

//參考: https://fabricmc.net/wiki/tutorial:items_docs
object ItemRegistry {

    val dice: Dice = Dice(FabricItemSettings())
    val mahjongTile: MahjongTile = MahjongTile(FabricItemSettings())
    val mahjongScoringStick: MahjongScoringStick = MahjongScoringStick(FabricItemSettings())
    //考慮加入牌尺

    fun register() {
        Registry.register(Registries.ITEM, id("dice"), dice)
        Registry.register(Registries.ITEM, id("mahjong_tile"), mahjongTile)
        Registry.register(Registries.ITEM, id("mahjong_scoring_stick"), mahjongScoringStick)
        ItemGroupEvents.modifyEntriesEvent(itemGroup).register {
            it.add(dice)
            it.add(mahjongTile)
            it.add(mahjongScoringStick)
        }
    }
}