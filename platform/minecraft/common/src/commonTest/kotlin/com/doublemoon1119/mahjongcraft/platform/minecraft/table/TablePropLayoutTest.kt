package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [TablePropPresentation] 的中央區域與內容驗證。 */
class TablePropLayoutTest {
    /** 中央物件的配置可重現，完整邊界位於共用區域內。 */
    @Test
    fun `center placements are deterministic and inside shared region`() {
        val presentation = presentation(
            TablePropPlacement(
                id = "example:bonus-a",
                kind = "example:tile",
                content = TablePropContent.CustomVariant("1"),
                location = TablePropLocation.Center(order = 0),
            ),
            TablePropPlacement(
                id = "example:bonus-b",
                kind = "example:tile",
                content = TablePropContent.CustomVariant("2"),
                location = TablePropLocation.Center(order = 1),
            ),
        )
        val definitions = mapOf(
            "example:tile" to TablePropKindDefinition(
                id = "example:tile",
                dimensions = TablePropDimensions(width = 0.1, height = 0.1, depth = 0.1),
                acceptsContent = { it == TablePropContent.CustomVariant("1") || it == TablePropContent.CustomVariant("2") },
            ),
        )

        val first = assertIs<TablePropLayoutResult.Success>(presentation.targets(definitions))
        val second = assertIs<TablePropLayoutResult.Success>(presentation.targets(definitions))

        assertEquals(first.targets, second.targets)
        assertTrue(first.targets.all { target -> target.x in 0.0..1.0 && target.z in 0.0..1.0 })
    }

    /** 未通過種類內容驗證時，不產生部分配置。 */
    @Test
    fun `unknown content is rejected before placement`() {
        val presentation = presentation(
            TablePropPlacement(
                id = "example:bonus-a",
                kind = "example:tile",
                content = TablePropContent.CustomVariant("unknown"),
                location = TablePropLocation.Center(order = 0),
            ),
        )
        val result = presentation.targets(
            mapOf(
                "example:tile" to TablePropKindDefinition(
                    id = "example:tile",
                    dimensions = TablePropDimensions(0.1, 0.1, 0.1),
                    acceptsContent = { it == TablePropContent.CustomVariant("known") },
                ),
            ),
        )

        assertIs<TablePropLayoutResult.Invalid>(result)
    }

    /** 完整尺寸超出中央區域時拒絕配置。 */
    @Test
    fun `center allocation rejects an object that cannot fit`() {
        val presentation = presentation(
            TablePropPlacement(
                id = "example:oversized",
                kind = "example:oversized",
                content = TablePropContent.CustomVariant("default"),
                location = TablePropLocation.Center(order = 0),
            ),
        )
        val result = presentation.targets(
            mapOf(
                "example:oversized" to TablePropKindDefinition(
                    id = "example:oversized",
                    dimensions = TablePropDimensions(0.8, 0.1, 0.8),
                ),
            ),
        )

        assertIs<TablePropLayoutResult.Invalid>(result)
    }

    /** 不同尺寸可混排，輸入順序不改變配置。 */
    @Test
    fun `center allocation is independent of input order and supports mixed sizes`() {
        val first = presentation(
            placement("example:chip", 2),
            placement("example:tile", 1),
            placement("other:tile", 0),
        )
        val reversed = presentation(
            placement("other:tile", 0),
            placement("example:tile", 1),
            placement("example:chip", 2),
        )
        val definitions = mapOf(
            "example:chip" to TablePropKindDefinition("example:chip", TablePropDimensions(0.04, 0.02, 0.04)),
            "example:tile" to TablePropKindDefinition("example:tile", TablePropDimensions(0.12, 0.15, 0.08)),
            "other:tile" to TablePropKindDefinition("other:tile", TablePropDimensions(0.08, 0.15, 0.08)),
        )

        val firstTargets = assertIs<TablePropLayoutResult.Success>(first.targets(definitions)).targets.sortedBy { it.id }
        val reversedTargets = assertIs<TablePropLayoutResult.Success>(reversed.targets(definitions)).targets.sortedBy { it.id }
        assertEquals(firstTargets, reversedTargets)
    }

    /** 四種桌子朝向皆保留完整中央邊界。 */
    @Test
    fun `center allocation stays inside the region for every table facing`() {
        val definition = mapOf(
            "example:tile" to TablePropKindDefinition("example:tile", TablePropDimensions(0.12, 0.15, 0.08)),
        )
        MahjongTableFacing.entries.forEach { facing ->
            val result = assertIs<TablePropLayoutResult.Success>(
                presentation(placement("example:tile", 0), tableFacing = facing).targets(definition),
            )
            val target = result.targets.single()
            val half = TablePropPresentation.CENTER_HALF_EXTENT
            assertTrue(target.x - 0.12 / 2.0 >= 0.5 - half)
            assertTrue(target.x + 0.12 / 2.0 <= 0.5 + half)
            assertTrue(target.z - 0.08 / 2.0 >= 0.5 - half)
            assertTrue(target.z + 0.08 / 2.0 <= 0.5 + half)
        }
    }

    /** 中央物件避開跨入中央的座位物件。 */
    @Test
    fun `center allocation avoids a seat prop that reaches into the center`() {
        val seat = TablePropPlacement(
            id = "example:seat-blocker",
            kind = "example:blocker",
            content = TablePropContent.CustomVariant("default"),
            location = TablePropLocation.Seat(
                seatIndex = 0,
                anchor = TableSeatAnchor.DISCARD_INNER_EDGE,
                offset = TableSeatOffset(z = -0.5),
            ),
        )
        val center = placement("example:center", 0)
        val definitions = mapOf(
            "example:blocker" to TablePropKindDefinition("example:blocker", TablePropDimensions(0.3, 0.3, 0.3)),
            "example:center" to TablePropKindDefinition("example:center", TablePropDimensions(0.3, 0.3, 0.3)),
        )
        val result = assertIs<TablePropLayoutResult.Success>(presentation(seat, center).targets(definitions))
        assertTrue(result.targets.map { it.id }.containsAll(listOf("example:seat-blocker", "example:center")))
        val centerTarget = result.targets.first { it.id == "example:center" }
        val seatTarget = result.targets.first { it.id == "example:seat-blocker" }
        assertTrue(centerTarget.x != seatTarget.x || centerTarget.z != seatTarget.z)
        assertTrue(
            !TablePropBounds.of(centerTarget, definitions.getValue("example:center").dimensions)
                .overlaps(TablePropBounds.of(seatTarget, definitions.getValue("example:blocker").dimensions)),
        )
    }

    /** 垂直分層的物件可上下相接而不重疊。 */
    @Test
    fun `vertically separated seat props do not overlap`() {
        val definitions = mapOf(
            "example:stick" to TablePropKindDefinition("example:stick", TablePropDimensions(0.1, 0.05, 0.1)),
        )
        val result = presentation(
            TablePropPlacement("example:stick-low", "example:stick", TablePropContent.CustomVariant("a"), TablePropLocation.Seat(0, TableSeatAnchor.MELD_CORNER, TableSeatOffset(y = 0.0))),
            TablePropPlacement("example:stick-high", "example:stick", TablePropContent.CustomVariant("b"), TablePropLocation.Seat(0, TableSeatAnchor.MELD_CORNER, TableSeatOffset(y = 0.1))),
        ).targets(definitions)
        assertIs<TablePropLayoutResult.Success>(result)
    }

    /** 非有限轉角不得進入幾何計算。 */
    @Test
    fun `non finite center rotation is rejected`() {
        val result = presentation(
            TablePropPlacement(
                id = "example:nan",
                kind = "example:tile",
                content = TablePropContent.CustomVariant("default"),
                location = TablePropLocation.Center(order = 0, yaw = Float.NaN),
            ),
        ).targets(mapOf("example:tile" to TablePropKindDefinition("example:tile", TablePropDimensions(0.1, 0.1, 0.1))))

        assertIs<TablePropLayoutResult.Invalid>(result)
    }

    /** 同樣的物件不因規則身分得到不同的中央保留區。 */
    @Test
    fun `rules receive identical space without a riichi reservation`() {
        val scene = presentation(placement("example:tile", 0))
        val definitions = mapOf("example:tile" to TablePropKindDefinition("example:tile", TablePropDimensions(0.7, 0.05, 0.7)))
        val custom = assertIs<TablePropLayoutResult.Success>(scene.targets(definitions)).targets.single()
        val riichi = assertIs<TablePropLayoutResult.Success>(scene.copy(ruleModuleId = "mahjongcraft:riichi").targets(definitions)).targets.single()
        assertEquals(custom.copy(ruleModuleId = riichi.ruleModuleId), riichi)
    }

    /** 中央保留依旋轉後的完整範圍判斷，不會只檢查中心點或忽略朝向。 */
    @Test
    fun `center reservation uses rotated bounds rather than the anchor point`() {
        val scene = presentation()
        val target = TablePropTarget(
            id = "example:edge",
            ruleModuleId = "example:rule",
            kind = "example:stick",
            content = TablePropContent.CustomVariant("default"),
            x = 1.02,
            y = 65.0,
            z = 0.5,
            yaw = 0f,
        )
        val dimensions = TablePropDimensions(0.4, 0.01, 0.02)
        assertTrue(intersectsTablePropCenter(scene.tableLocation, target, dimensions))
        assertFalse(intersectsTablePropCenter(scene.tableLocation, target.copy(yaw = 90f), dimensions))
    }

    /** 第三方設定的任意有限轉角會隨桌子旋轉，並以旋轉後的完整尺寸檢查可用空間。 */
    @Test
    fun `custom center yaw follows table facing and affects occupied space`() {
        val placement = placement("example:tile", 0)
        val scene = presentation(
            placement.copy(location = TablePropLocation.Center(order = 0, yaw = 45f)),
            tableFacing = MahjongTableFacing.EAST,
        )
        val dimensions = mapOf("example:tile" to TablePropKindDefinition("example:tile", TablePropDimensions(0.6, 0.05, 0.2)))
        val target = assertIs<TablePropLayoutResult.Success>(scene.targets(dimensions)).targets.single()
        assertEquals(135f, target.yaw)

        val wide = mapOf("example:tile" to TablePropKindDefinition("example:tile", TablePropDimensions(0.78, 0.05, 0.78)))
        assertIs<TablePropLayoutResult.Success>(presentation(placement).targets(wide))
        assertIs<TablePropLayoutResult.Invalid>(scene.targets(wide))
    }

    /**
     * 建立中央物件的固定測試描述。
     *
     * @param id 種類與物件的完整識別碼。
     * @param order 中央配置順序。
     * @return 使用預設內容的中央物件。
     */
    private fun placement(id: String, order: Int) = TablePropPlacement(
        id = id,
        kind = id,
        content = TablePropContent.CustomVariant("default"),
        location = TablePropLocation.Center(order),
    )

    /**
     * 建立固定座標與四人座位的測試場景。
     *
     * @param placements 場景中的全部物件。
     * @param tableFacing 測試用桌子朝向。
     * @return 尚未執行幾何配置的場景。
     */
    private fun presentation(
        vararg placements: TablePropPlacement,
        tableFacing: MahjongTableFacing = MahjongTableFacing.SOUTH,
    ) = TablePropPresentation(
        ruleModuleId = "example:rule",
        tableId = Uuid.random(),
        tableLocation = TableLocation(dimensionId = "minecraft:overworld", x = 0, y = 64, z = 0),
        tableFacing = tableFacing,
        seatLayout = MahjongTableSeatLayout.FOUR_PLAYER,
        placements = placements.toList(),
    )
}
