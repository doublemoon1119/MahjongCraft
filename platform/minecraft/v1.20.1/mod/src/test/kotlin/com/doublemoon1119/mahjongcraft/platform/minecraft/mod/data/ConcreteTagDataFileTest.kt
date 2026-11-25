package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.data

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.DataPackLayout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 驗證 1.20.1 自備的混凝土標籤。這個版本的 Fabric 與 Forge 沒有共通的混凝土標籤，配方改用這個標籤。 */
class ConcreteTagDataFileTest {
    /** 原版的全部混凝土 item ID。 */
    private val concreteIds = setOf(
        "minecraft:white_concrete",
        "minecraft:orange_concrete",
        "minecraft:magenta_concrete",
        "minecraft:light_blue_concrete",
        "minecraft:yellow_concrete",
        "minecraft:lime_concrete",
        "minecraft:pink_concrete",
        "minecraft:gray_concrete",
        "minecraft:light_gray_concrete",
        "minecraft:cyan_concrete",
        "minecraft:purple_concrete",
        "minecraft:blue_concrete",
        "minecraft:brown_concrete",
        "minecraft:green_concrete",
        "minecraft:red_concrete",
        "minecraft:black_concrete",
    )

    /** 驗證混凝土標籤完整列出 16 種原版混凝土。 */
    @Test
    fun `concretes tag contains every vanilla concrete color`() {
        val resourcePath = "/${DataPackLayout.ITEM_TAGS}/concretes.json"
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }
        val values = assertIs<JsonArray>(Json.parseToJsonElement(content).jsonObject["values"]).mapTo(mutableSetOf()) {
            assertIs<JsonPrimitive>(it).content
        }

        assertEquals(concreteIds, values)
    }
}
