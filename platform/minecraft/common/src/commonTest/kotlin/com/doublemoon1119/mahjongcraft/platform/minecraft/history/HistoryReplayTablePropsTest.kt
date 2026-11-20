package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropDefinitions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatAnchor
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證牌譜回放的桌上物件擺放。 */
class HistoryReplayTablePropsTest {
    /** 座位上的點棒擺在該座位，客戶端不認得的種類不畫也不佔位置。 */
    @Test
    fun `known props are placed and unknown kinds are skipped`() {
        val stick = TablePropPlacement(
            id = "test:stick",
            kind = BuiltInTablePropDefinitions.SCORING_STICK.id,
            content = TablePropContent.CustomVariant("1000"),
            location = TablePropLocation.Seat(1, TableSeatAnchor.DISCARD_INNER_EDGE, TableSeatOffset(x = 0.0, y = 0.0, z = 0.0), 0f),
        )
        val unknown = TablePropPlacement(
            id = "test:unknown",
            kind = "test:unknown_kind",
            content = TablePropContent.CustomVariant("a"),
            location = TablePropLocation.Center(0, 0f),
        )
        val targets = HistoryReplayTableProps.targets(
            placements = listOf(stick, unknown),
            ruleModuleId = "test:rule",
            playerCount = 4,
            arranged = emptyMap(),
            definitions = mapOf(BuiltInTablePropDefinitions.SCORING_STICK.id to BuiltInTablePropDefinitions.SCORING_STICK),
        )
        assertEquals(expected = listOf("test:stick"), actual = targets.map { it.id })
        assertEquals(expected = "test:rule", actual = targets.single().ruleModuleId)
    }

    /** 中央物件擺在桌子中央附近，沒有可畫的物件時為空。 */
    @Test
    fun `center props stay near the table centre`() {
        val center = TablePropPlacement(
            id = "test:center",
            kind = BuiltInTablePropDefinitions.SCORING_STICK.id,
            content = TablePropContent.CustomVariant("100"),
            location = TablePropLocation.Center(0, 0f),
        )
        val definitions = mapOf(BuiltInTablePropDefinitions.SCORING_STICK.id to BuiltInTablePropDefinitions.SCORING_STICK)
        val target = HistoryReplayTableProps.targets(listOf(center), "test:rule", 4, emptyMap(), definitions).single()
        assertTrue(target.x in 0.0..1.0 && target.z in 0.0..1.0, "Center prop should be on the table block: $target")
        assertEquals(expected = emptyList(), actual = HistoryReplayTableProps.targets(listOf(center), "test:rule", 4, emptyMap(), emptyMap()))
    }
}
