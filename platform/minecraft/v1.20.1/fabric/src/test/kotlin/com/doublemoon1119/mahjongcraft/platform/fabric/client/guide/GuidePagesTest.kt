package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideViewer
import com.doublemoon1119.mahjongcraft.platform.minecraft.guide.MinecraftGuideScreenKeys
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證「麻將基礎」頁的牌例前後一致。 */
class GuidePagesTest {
    /** 「麻將基礎」頁的區塊。 */
    private val basics = GuidePages.blocks(GuideTab.BASICS, GuideViewer(id = Uuid.fromLongs(0L, 1L), name = "Viewer"))

    /** 摸到東、打出北之後，手牌仍是 13 張，而且正好是開始的手牌換掉北、加上東。 */
    @Test
    fun `the draw and discard example keeps a consistent hand`() {
        val start = groups(section(MinecraftGuideScreenKeys.BASICS_ROUND_STEP_HAND)).single()
        val (handBeforeDraw, drawn) = groups(section(MinecraftGuideScreenKeys.BASICS_ROUND_STEP_DRAW))
        val (handAfter, river) = groups(section(MinecraftGuideScreenKeys.BASICS_ROUND_STEP_DISCARD))

        assertEquals(13, start.size)
        assertEquals(start, handBeforeDraw)
        assertEquals(listOf<Tile>(Tile.Honor.East), drawn)
        assertEquals(listOf<Tile>(Tile.Honor.North), river)
        assertEquals(13, handAfter.size)
        assertEquals(counts(start - Tile.Honor.North + Tile.Honor.East), counts(handAfter))
    }

    /** 每種鳴牌的組成都正好是來源加上已有的牌。 */
    @Test
    fun `every meld result combines its source and existing tiles`() {
        listOf(
            MinecraftGuideScreenKeys.BASICS_CHI,
            MinecraftGuideScreenKeys.BASICS_PON,
            MinecraftGuideScreenKeys.BASICS_OPEN_KAN,
            MinecraftGuideScreenKeys.BASICS_ADDED_KAN,
            MinecraftGuideScreenKeys.BASICS_CLOSED_KAN,
        ).forEach { key ->
            val (source, existing, result) = groups(section(key))
            assertEquals(counts(source + existing), counts(result), key)
        }
    }

    /** 兩種和牌使用同一手牌與同一張最後的牌，只差在牌的來源。 */
    @Test
    fun `both win examples share the hand and the last tile`() {
        val ron = groups(section(MinecraftGuideScreenKeys.BASICS_RON))
        val tsumo = groups(section(MinecraftGuideScreenKeys.BASICS_TSUMO))

        assertEquals(ron, tsumo)
        assertEquals(13, ron.dropLast(1).sumOf { it.size })
        assertTrue(ron.last().size == 1)
    }

    /** 麻將基礎頁最後是開啟規則一覽「基本玩法」的按鈕。 */
    @Test
    fun `basics page ends with the rule basics button`() {
        val button = basics.last() as GuideBlock.Button

        assertEquals(GuideAction.RULE_BASICS, button.action)
    }

    /**
     * 以標題的翻譯鍵找出分隔段落或卡片的內容。
     *
     * @param titleKey 標題的翻譯鍵。
     * @return 標題下方的區塊。
     */
    private fun section(titleKey: String): List<GuideBlock> = basics.mapNotNull { block ->
        when (block) {
            is GuideBlock.Section -> block.body.takeIf { key(block.title) == titleKey }
            is GuideBlock.Card -> block.body.takeIf { key(block.title) == titleKey }
            else -> null
        }
    }.single()

    /**
     * 區塊中各牌組的牌，依出現順序排列。
     *
     * @param body 區塊。
     * @return 各牌組的牌。
     */
    private fun groups(body: List<GuideBlock>): List<List<Tile>> = body
        .filterIsInstance<GuideBlock.Tiles>()
        .flatMap { it.items }
        .filterIsInstance<GuideTileItem.Group>()
        .map { it.tiles }

    /**
     * 各種牌的張數。
     *
     * @param tiles 牌。
     * @return 每種牌的張數。
     */
    private fun counts(tiles: List<Tile>): Map<Tile, Int> = tiles.groupingBy { it }.eachCount()

    /**
     * 翻譯文字的翻譯鍵。
     *
     * @param text 翻譯文字。
     * @return 翻譯鍵。
     */
    private fun key(text: Component): String = (text.contents as TranslatableContents).key
}
