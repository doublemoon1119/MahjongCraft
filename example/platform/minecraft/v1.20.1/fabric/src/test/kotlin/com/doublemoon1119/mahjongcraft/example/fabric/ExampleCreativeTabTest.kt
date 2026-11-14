package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.fabric.registry.ExampleItemGroups
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import kotlin.test.Test
import kotlin.test.assertEquals

/** 檢查分頁填入物品時使用的同一份家具清單；原生註冊由遊戲執行環境驗證。 */
class ExampleCreativeTabTest {
    /** 分頁依序列出座位、人偶與麻將桌，不提供暫時座位或代理物品。 */
    @Test
    fun `creative tab contents contain the furniture and tables in order`() {
        assertEquals(
            expected = listOf(
                ExampleFurnitureIds.CHAIR,
                ExampleFurnitureIds.CUSHION,
                ExampleFurnitureIds.BENCH,
                ExampleCharacterIds.ACTOR,
                ExampleFurnitureIds.ONE_BLOCK_TABLE,
                ExampleFurnitureIds.LOW_TABLE,
                ExampleFurnitureIds.ROUND_TABLE,
            ),
            actual = ExampleItemGroups.itemIds(),
        )
    }
}
