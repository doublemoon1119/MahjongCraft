package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證規則桌面物件的落點換算與差量同步比對。 */
class TablePropSyncTest {
    /** 測試用的既有物件；[target] 為 null 代表無法辨識身分。 */
    private data class Existing(val name: String, val target: TablePropTarget?)

    private fun target(
        variant: String = "1000",
        x: Double = 0.0,
        yaw: Float = 0f,
        kind: String = BuiltInTablePropKinds.SCORING_STICK,
    ) = TablePropTarget(kind = kind, variant = variant, x = x, y = 65.0, z = 0.0, yaw = yaw)

    /** 已在正確落點的物件原樣保留，只生成缺少的、移除多出來的。 */
    @Test
    fun `keeps matching props, spawns missing ones and removes the rest`() {
        val kept = Existing("kept", target(x = 1.0))
        val stale = Existing("stale", target(x = 9.0))

        val plan = planTablePropSync(
            targets = listOf(target(x = 1.0), target(x = 2.0)),
            existing = listOf(kept, stale),
            targetOf = Existing::target,
        )

        assertEquals(listOf(target(x = 2.0)), plan.missing)
        assertEquals(listOf(stale), plan.surplus)
    }

    /** 變體或種類不同的物件即使落點相同也要換掉。 */
    @Test
    fun `a different variant or kind at the same spot is replaced`() {
        val wrongVariant = Existing("100", target(variant = "100"))
        val wrongKind = Existing("other", target(kind = "example:marker"))

        val plan = planTablePropSync(
            targets = listOf(target()),
            existing = listOf(wrongVariant, wrongKind),
            targetOf = Existing::target,
        )

        assertEquals(listOf(target()), plan.missing)
        assertEquals(setOf(wrongVariant, wrongKind), plan.surplus.toSet())
    }

    /** 同一落點有兩個目標時，一個既有物件只抵銷其中一個。 */
    @Test
    fun `one existing prop satisfies only one of two identical targets`() {
        val existing = Existing("only", target())

        val plan = planTablePropSync(
            targets = listOf(target(), target()),
            existing = listOf(existing),
            targetOf = Existing::target,
        )

        assertEquals(listOf(target()), plan.missing)
        assertTrue(plan.surplus.isEmpty())
    }

    /** 無法辨識身分的既有物件一律移除。 */
    @Test
    fun `unidentified props are removed`() {
        val unknown = Existing("unknown", null)

        val plan = planTablePropSync(targets = emptyList(), existing = listOf(unknown), targetOf = Existing::target)

        assertEquals(listOf(unknown), plan.surplus)
    }

    /** 座標的浮點誤差與 `180`／`-180` 這種同一朝向的不同寫法都視為同一個落點。 */
    @Test
    fun `tolerates float round trip and equivalent yaw`() {
        val existing = Existing("rounded", target(x = 1.00005, yaw = -180f))

        val plan = planTablePropSync(
            targets = listOf(target(x = 1.0, yaw = 180f)),
            existing = listOf(existing),
            targetOf = Existing::target,
        )

        assertTrue(plan.missing.isEmpty())
        assertTrue(plan.surplus.isEmpty())
    }

    /** 描述換算成世界落點時，與通用 layout 的座位錨點換算一致，且保留描述的種類與變體。 */
    @Test
    fun `targets convert seat-local placements through the table layout`() {
        val placement = TablePropPlacement(
            kind = BuiltInTablePropKinds.SCORING_STICK,
            variant = "100",
            seatIndex = 2,
            anchor = TableSeatAnchor.MELD_CORNER,
            offset = TableSeatOffset(x = -0.1, y = 0.02, z = -0.03),
            yawOffset = 90f,
        )
        val presentation = TablePropPresentation(
            tableId = Uuid.random(),
            tableLocation = TableLocation(dimensionId = "minecraft:overworld", x = 12, y = 64, z = -7),
            tableFacing = MahjongTableFacing.EAST,
            placements = listOf(placement),
        )

        val world = MahjongTileTableLayout.seatAnchorPlacement(
            controllerX = 12,
            controllerY = 64,
            controllerZ = -7,
            tableFacing = MahjongTableFacing.EAST,
            seatIndex = 2,
            anchor = TableSeatAnchor.MELD_CORNER,
            offset = placement.offset,
            yawOffset = 90f,
        )
        assertEquals(
            listOf(TablePropTarget(kind = BuiltInTablePropKinds.SCORING_STICK, variant = "100", x = world.x, y = world.y, z = world.z, yaw = world.yaw)),
            presentation.targets(),
        )
    }
}
