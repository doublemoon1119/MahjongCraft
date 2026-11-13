package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftHudKeys
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證捨牌來源文字：自己、入座看他家、旁觀三種說法。 */
class DiscardSourceTextTest {
    /** 自己打的牌只說是自己，不查名字。 */
    @Test
    fun `own discards say so without a name`() {
        val content = discardSourceText(RelativeDirection.Self) { error("The name is not needed") }.translatable()

        assertEquals(MinecraftHudKeys.DISCARD_SOURCE_SELF, content.key)
        assertTrue(content.args.isEmpty())
    }

    /** 入座時加上打出者的相對位置與名字。 */
    @Test
    fun `seated players see the relation and the name`() {
        val content = discardSourceText(RelativeDirection.Left) { "Steve" }.translatable()

        assertEquals(MinecraftHudKeys.DISCARD_SOURCE_SEATED, content.key)
        assertEquals(MinecraftHudKeys.RELATION_LEFT, (content.args[0] as Text).translatable().key)
        assertEquals("Steve", content.args[1])
    }

    /** 旁觀時只有名字。 */
    @Test
    fun `spectators see only the name`() {
        val content = discardSourceText(null) { "Steve" }.translatable()

        assertEquals(MinecraftHudKeys.DISCARD_SOURCE, content.key)
        assertEquals(listOf<Any>("Steve"), content.args.toList())
    }

    /** @return 這段文字的翻譯內容。 */
    private fun Text.translatable(): TranslatableTextContent = content as TranslatableTextContent
}
