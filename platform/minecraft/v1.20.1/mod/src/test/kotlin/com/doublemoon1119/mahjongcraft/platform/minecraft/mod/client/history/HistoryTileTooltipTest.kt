package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證牌譜牌面滑鼠提示的內容與順序。 */
class HistoryTileTooltipTest {
    /** 一般的牌只顯示牌名。 */
    @Test
    fun `a plain tile shows only its name`() {
        val name = Component.literal("name")

        assertEquals(listOf<Component>(name), historyTileTooltip(name, catalogIndex = null, takenBy = null, markerLabels = emptyList()))
    }

    /** 牌索引在最前，接著是牌名、被誰鳴走與牌河標記。 */
    @Test
    fun `index name taken and markers appear in order`() {
        val tooltip = historyTileTooltip(
            name = Component.literal("name"),
            catalogIndex = 7,
            takenBy = Component.literal("taken"),
            markerLabels = listOf(Component.literal("marker")),
        )

        assertEquals(MinecraftHistoryScreenKeys.STATE_TILE_INDEX, (tooltip[0].contents as TranslatableContents).key)
        assertEquals("name", tooltip[1].string)
        assertEquals("taken", tooltip[2].string)
        assertEquals("marker", tooltip[3].string)
        assertEquals(4, tooltip.size)
    }

    /** 只有被鳴走的空位與副露中被鳴的牌會隨游標一起強調，其他說明或其他牌不強調。 */
    @Test
    fun `claim highlight links only the taken slot and the claimed meld tile`() {
        val claimed = listOf(HistoryTileNote(HistoryTileNoteKind.CLAIMED_FROM, Component.literal("from")))
        val addedKan = listOf(HistoryTileNote(HistoryTileNoteKind.ADDED_KAN, Component.literal("added")))

        assertTrue(isLinkedClaimHighlight(tile = 3, linkedTile = 3, isTaken = true, notes = emptyList()))
        assertTrue(isLinkedClaimHighlight(tile = 3, linkedTile = 3, isTaken = false, notes = claimed))
        assertFalse(isLinkedClaimHighlight(tile = 3, linkedTile = 3, isTaken = false, notes = addedKan))
        assertFalse(isLinkedClaimHighlight(tile = 3, linkedTile = 4, isTaken = true, notes = claimed))
        assertFalse(isLinkedClaimHighlight(tile = 3, linkedTile = null, isTaken = true, notes = claimed))
    }

    /** 牌譜牌面的牌名使用規則登記的名稱，而不是素材代號。 */
    @Test
    fun `history faces resolve registered names`() {
        val displayNames = TileDisplayNameRegistryImpl().apply { BundledMinecraftMahjongExtensions.all.forEach { it.registerTileDisplayNames(this) } }
        val face = TileFaceViewDto(TileDto.Extension(TaiwanTileTypes.SPRING.toString()), emptyList(), null)

        assertEquals(displayNames.find(TaiwanTileTypes.SPRING), (face.toHistoryNameText(displayNames).contents as TranslatableContents).key)
    }
}
