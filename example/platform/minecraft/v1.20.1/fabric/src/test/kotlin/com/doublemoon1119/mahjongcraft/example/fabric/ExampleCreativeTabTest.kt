package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.fabric.registry.ExampleItemGroups
import kotlin.test.Test
import kotlin.test.assertEquals

/** 檢查分頁填入物品時使用的同一份家具清單；原生註冊由遊戲執行環境驗證。 */
class ExampleCreativeTabTest {
    /** 分頁只列出椅子與坐墊，不提供暫時座位物品或生怪蛋。 */
    @Test
    fun `creative tab contents contain only chair and cushion in order`() {
        assertEquals(
            listOf(ExampleFurnitureIds.CHAIR, ExampleFurnitureIds.CUSHION),
            ExampleItemGroups.furnitureItemIds(),
        )
    }
}
