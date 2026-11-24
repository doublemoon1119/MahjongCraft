package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item.MahjongDiceItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item.MahjongHandbookItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item.MahjongRulerItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item.MahjongScoringStickItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.RegisteredEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.resourceId
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerColor
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item

/** MahjongCraft 一般物品的集中登記點；各物品在 [register] 登記完成後才能讀取。 */
object ModItems {
    /** 登記完成後可讀取 [MAHJONG_DICE] 的項目。 */
    private lateinit var mahjongDiceEntry: RegisteredEntry<Item>

    /** 登記完成後可讀取 [MAHJONG_TILE] 的項目。 */
    private lateinit var mahjongTileEntry: RegisteredEntry<Item>

    /** 登記完成後可讀取 [MAHJONG_HANDBOOK] 的項目。 */
    private lateinit var mahjongHandbookEntry: RegisteredEntry<Item>

    /** 登記完成後可讀取 [MAHJONG_SCORING_STICK] 的項目。 */
    private lateinit var mahjongScoringStickEntry: RegisteredEntry<Item>

    /** 登記完成後可讀取 [MAHJONG_RULERS] 各項的項目。 */
    private lateinit var mahjongRulerEntries: Map<MahjongRulerColor, RegisteredEntry<Item>>

    /** 可放置自由測試骰子的麻將骰子 item。 */
    val MAHJONG_DICE: Item
        get() = mahjongDiceEntry.get()

    /** 單一物品類型表示所有麻將牌面的 item。 */
    val MAHJONG_TILE: Item
        get() = mahjongTileEntry.get()

    /** 可由原版書與麻將牌合成的手冊，拿在手上按右鍵開啟麻將手冊快捷畫面。 */
    val MAHJONG_HANDBOOK: Item
        get() = mahjongHandbookEntry.get()

    /** 單一物品類型表示所有點棒面額的 item。 */
    val MAHJONG_SCORING_STICK: Item
        get() = mahjongScoringStickEntry.get()

    /** 每種顏色各一個的牌尺 item，依 [MahjongRulerColor] 的順序排列。 */
    val MAHJONG_RULERS: Map<MahjongRulerColor, Item> by lazy {
        mahjongRulerEntries.mapValues { (_, entry) -> entry.get() }
    }

    /**
     * 登記 MahjongCraft 的一般 item。
     *
     * @param registrar 寫入登記表的方式。
     */
    fun register(registrar: ModRegistrar) {
        mahjongDiceEntry = registrar.registerItem("mahjong_dice") { MahjongDiceItem(Item.Properties()) }
        mahjongTileEntry = registrar.registerItem("mahjong_tile") { MahjongTileItem(Item.Properties()) }
        mahjongHandbookEntry = registrar.registerItem("mahjong_handbook") { MahjongHandbookItem(Item.Properties()) }
        mahjongScoringStickEntry = registrar.registerItem("mahjong_scoring_stick") { MahjongScoringStickItem(Item.Properties()) }
        mahjongRulerEntries = MahjongRulerColor.entries.associateWith { color ->
            registrar.registerItem(color.itemPath) { MahjongRulerItem(color, Item.Properties()) }
        }
    }

    /**
     * 以 MahjongCraft 命名空間登記一個 item。
     *
     * @param path item 的登記路徑。
     * @param factory 建立 item 的函式。
     * @return 登記完成後可讀取 item 的項目。
     */
    private fun ModRegistrar.registerItem(path: String, factory: () -> Item): RegisteredEntry<Item> = register(
        registry = Registries.ITEM,
        id = resourceId(MinecraftModMetadata.MOD_ID, path),
        factory = factory,
    )
}
