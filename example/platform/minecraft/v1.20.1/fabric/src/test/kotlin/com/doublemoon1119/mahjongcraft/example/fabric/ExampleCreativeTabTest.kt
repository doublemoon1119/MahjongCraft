package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.fabric.registry.ExampleItemGroups
import kotlin.test.Test
import kotlin.test.assertEquals

/** 檢查分頁填入物品時使用的同一份家具清單；原生註冊由遊戲執行環境驗證。 */
class ExampleCreativeTabTest {
    /** 分頁依序列出椅子、坐墊與三種麻將桌，不提供暫時座位物品或生怪蛋。 */
    @Test
    fun `creative tab contents contain the furniture and tables in order`() {
        assertEquals(
            listOf(
                ExampleFurnitureIds.CHAIR,
                ExampleFurnitureIds.CUSHION,
                ExampleFurnitureIds.ONE_BLOCK_TABLE,
                ExampleFurnitureIds.LOW_TABLE,
                ExampleFurnitureIds.ROUND_TABLE,
            ),
            ExampleItemGroups.furnitureItemIds(),
        )
    }
}
