package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證牌譜牌面滑鼠提示的內容與順序。 */
class HistoryTileTooltipTest {
    /** 一般的牌只顯示牌名。 */
    @Test
    fun `a plain tile shows only its name`() {
        val name = Text.literal("name")

        assertEquals(listOf<Text>(name), historyTileTooltip(name, catalogIndex = null, taken = false, markerLabels = emptyList()))
    }

    /** 牌索引在最前，接著是牌名、被鳴走與牌河標記。 */
    @Test
    fun `index name taken and markers appear in order`() {
        val tooltip = historyTileTooltip(Text.literal("name"), catalogIndex = 7, taken = true, markerLabels = listOf(Text.literal("marker")))

        assertEquals(MinecraftHistoryScreenKeys.STATE_TILE_INDEX, (tooltip[0].content as TranslatableTextContent).key)
        assertEquals("name", tooltip[1].string)
        assertEquals(MinecraftHistoryScreenKeys.STATE_TAKEN, (tooltip[2].content as TranslatableTextContent).key)
        assertEquals("marker", tooltip[3].string)
        assertEquals(4, tooltip.size)
    }

    /** 牌譜牌面的牌名使用規則登記的名稱，而不是素材代號。 */
    @Test
    fun `history faces resolve registered names`() {
        val displayNames = TileDisplayNameRegistryImpl().apply { BundledMinecraftMahjongExtensions.all.forEach { it.registerTileDisplayNames(this) } }
        val face = TileFaceViewDto(TileDto.Extension(TaiwanTileTypes.SPRING.toString()), emptyList(), null)

        assertEquals(displayNames.find(TaiwanTileTypes.SPRING), (face.toHistoryNameText(displayNames).content as TranslatableTextContent).key)
    }
}
