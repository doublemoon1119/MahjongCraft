package com.doublemoon1119.mahjongcraft.platform.minecraft.metadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證 Minecraft resource identifier 的接受範圍。 */
class MinecraftResourceIdsTest {
    /** 原版 dimension、sound 與 texture identifier 皆被接受。 */
    @Test
    fun `vanilla identifiers are valid`() {
        listOf(
            "minecraft:overworld",
            "minecraft:entity.experience_orb.pickup",
            "mahjongcraft:textures/gui/portrait.png",
        ).forEach { value ->
            assertTrue(MinecraftResourceIds.isValid(value), "Expected valid identifier: $value")
        }
    }

    /** 大寫、空白、缺少 namespace 與多個冒號皆被拒絕。 */
    @Test
    fun `malformed identifiers are invalid`() {
        listOf(
            "overworld",
            "Minecraft:overworld",
            "minecraft:the end",
            ":overworld",
            "minecraft:overworld:extra",
        ).forEach { value ->
            assertFalse(MinecraftResourceIds.isValid(value), "Expected invalid identifier: '$value'")
        }
    }

    /** 不合法時以呼叫端提供的訊息失敗。 */
    @Test
    fun `requireValid fails with the caller message`() {
        val error = assertFailsWith<IllegalArgumentException> {
            MinecraftResourceIds.requireValid("Overworld") { "Invalid dimension identifier: Overworld" }
        }

        assertEquals("Invalid dimension identifier: Overworld", error.message)
    }
}
