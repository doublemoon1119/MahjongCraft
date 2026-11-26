package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.MinecraftTestBootstrap
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.Optional
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證把滑鼠提示展開成一般文字，供看不到提示的指令來源閱讀。 */
class HoverExpandedTextTest {
    @BeforeTest
    fun bootstrapMinecraft() {
        MinecraftTestBootstrap.ensure()
    }

    /** 沒有提示的訊息內容不變。 */
    @Test
    fun `keeps text without hover`() {
        val message = Component.literal("Current settings").append(Component.literal(" are loaded"))

        assertEquals("Current settings are loaded", message.withHoverTextExpanded().string)
    }

    /** 單行提示接在標籤的同一行。 */
    @Test
    fun `places a single line hover after its label`() {
        val message = Component.literal("Reload failed ").append(
            bracketedInteractiveLabel(label = Component.literal("Details"), hoverText = Component.literal("file is missing")),
        )

        assertEquals("Reload failed [Details] file is missing", message.withHoverTextExpanded().string)
    }

    /** 多行提示從下一行縮排列出。第一行重複標籤前的文字時略過。 */
    @Test
    fun `lists a multi line hover below its label without a repeated title`() {
        val hover = Component.literal("Mahjong Table").append(Component.literal("\nBreak policy: owner")).append(Component.literal("\nOrphans: keep"))
        val message = Component.literal("  • ").append(Component.literal("Mahjong Table")).append(" ")
            .append(bracketedInteractiveLabel(label = Component.literal("Settings"), hoverText = hover))

        assertEquals(
            expected = "  • Mahjong Table [Settings]\n      Break policy: owner\n      Orphans: keep",
            actual = message.withHoverTextExpanded().string,
        )
    }

    /** 多行提示的第一行不是重複文字時保留。 */
    @Test
    fun `keeps the first line of a multi line hover that adds information`() {
        val hover = Component.literal("Path: config/server.toml").append(Component.literal("\nTimeout: 60s"))
        val message = Component.literal("Settings ").append(bracketedInteractiveLabel(label = Component.literal("Details"), hoverText = hover))

        assertEquals(
            expected = "Settings [Details]\n      Path: config/server.toml\n      Timeout: 60s",
            actual = message.withHoverTextExpanded().string,
        )
    }

    /** 展開後不留下任何提示或點擊動作。 */
    @Test
    fun `removes hover and click events`() {
        val message = Component.literal("Settings ").append(
            bracketedInteractiveLabel(label = Component.literal("Details"), hoverText = Component.literal("value")),
        )
        var interactive = false

        message.withHoverTextExpanded().visit(
            { style: Style, _: String ->
                if (style.hoverEvent != null || style.clickEvent != null) interactive = true
                Optional.empty<Unit>()
            },
            Style.EMPTY,
        )

        assertTrue(!interactive, "Expanded text must not keep hover or click events")
    }
}
