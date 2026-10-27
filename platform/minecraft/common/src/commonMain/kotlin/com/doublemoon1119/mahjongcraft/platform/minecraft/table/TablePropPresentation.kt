package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import kotlin.math.abs
import kotlin.uuid.Uuid

/**
 * 一張桌子此刻應有的全部規則桌面物件，座位與中央物件共用占用檢查。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property ruleModuleId 產生描述的規則模組，用來選擇公開外觀。
 * @property tableLocation 麻將桌的基準方塊位置。
 * @property tableFacing 桌子的水平朝向。
 * @property seatLayout 完整玩家席位對應的標準布局。
 * @property placements 規則描述的完整清單；空清單會清除所有規則物件。
 */
data class TablePropPresentation(
    val tableId: Uuid,
    val ruleModuleId: String,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val placements: List<TablePropPlacement>,
) {
    /**
     * 驗證座位物件，再按穩定順序將中央物件放到第一個可用位置。
     *
     * 旋轉尺寸使用保守包圍盒。配置順序不受輸入清單順序影響，也不保證能找到最佳裝箱方式。
     * 工作量超出保護上限時明確失敗，不嘗試無上限搜尋。
     *
     * @param definitions 登記時固定的種類定義。
     * @return 完整配置；任一內容、位置或占用無效時為失敗，不回傳部分結果。
     */
    fun targets(definitions: Map<String, TablePropKindDefinition>): TablePropLayoutResult {
        if (!validId(ruleModuleId)) return TablePropLayoutResult.Invalid("Invalid table prop rule: $ruleModuleId")
        if (placements.size > MAX_PLACEMENTS) return TablePropLayoutResult.Invalid("LIMIT_EXCEEDED: too many table props")
        if (placements.map { it.id }.toSet().size != placements.size) return TablePropLayoutResult.Invalid("Duplicate table prop ID")
        placements.forEach { placement ->
            if (!validId(placement.id) || !validId(placement.kind)) return TablePropLayoutResult.Invalid("Invalid table prop ID: ${placement.id}")
            val definition = definitions[placement.kind] ?: return TablePropLayoutResult.Invalid("Unknown table prop kind: ${placement.kind}")
            if (definition.id != placement.kind || !definition.accepts(placement.content)) return TablePropLayoutResult.Invalid("Unsupported table prop content: ${placement.id}")
        }
        val placed = mutableListOf<TablePropTarget>()
        val occupied = mutableListOf<TablePropBounds>()
        for (placement in placements.filter { it.location is TablePropLocation.Seat }.sortedBy { it.id }) {
            val location = placement.location as TablePropLocation.Seat
            if (location.seatIndex !in 0 until seatLayout.playerCount ||
                !location.yawOffset.isFinite() ||
                !location.offset.x.isFinite() ||
                !location.offset.y.isFinite() ||
                !location.offset.z.isFinite()
            ) {
                return TablePropLayoutResult.Invalid("Invalid seat placement: ${placement.id}")
            }
            val world = MahjongTileTableLayout.seatAnchorPlacement(
                controllerX = tableLocation.x,
                controllerY = tableLocation.y,
                controllerZ = tableLocation.z,
                tableFacing = tableFacing,
                seatLayout = seatLayout,
                seatIndex = location.seatIndex,
                anchor = location.anchor,
                offset = location.offset,
                yawOffset = location.yawOffset,
            )
            val target = TablePropTarget(placement.id, ruleModuleId, placement.kind, placement.content, world.x, world.y, world.z, world.yaw)
            val bounds = TablePropBounds.of(toLocal(target), definitions.getValue(placement.kind).dimensions)
            if (occupied.any { it.overlaps(bounds) }) return TablePropLayoutResult.Invalid("Overlapping seat props: ${placement.id}")
            placed += target
            occupied += bounds
        }
        for (
        placement in placements.filter { it.location is TablePropLocation.Center }
            .sortedWith(compareBy<TablePropPlacement> { (it.location as TablePropLocation.Center).order }.thenBy { it.id })
        ) {
            val location = placement.location as TablePropLocation.Center
            if (!location.yaw.isFinite()) return TablePropLayoutResult.Invalid("Invalid center angle: ${placement.id}")
            val dimensions = definitions.getValue(placement.kind).dimensions
            val origin = TablePropTarget(placement.id, ruleModuleId, placement.kind, placement.content, 0.0, 0.0, 0.0, location.yaw)
            val size = TablePropBounds.of(origin, dimensions)
            val halfX = size.maxX
            val halfZ = size.maxZ
            if (halfX > CENTER_HALF_EXTENT || halfZ > CENTER_HALF_EXTENT) return TablePropLayoutResult.Invalid("NO_SPACE: ${placement.id}")
            val xs = candidates(halfX, occupied.flatMap { listOf(it.minX - CENTER_GAP - halfX, it.maxX + CENTER_GAP + halfX) })
            val zs = candidates(halfZ, occupied.flatMap { listOf(it.minZ - CENTER_GAP - halfZ, it.maxZ + CENTER_GAP + halfZ) })
            var attempts = 0
            var selected: TablePropTarget? = null
            search@ for (z in zs) {
                for (x in xs) {
                    if (++attempts > MAX_CENTER_CANDIDATES) return TablePropLayoutResult.Invalid("LIMIT_EXCEEDED: ${placement.id}")
                    val candidate = origin.copy(x = x, z = z)
                    val bounds = TablePropBounds.of(candidate, dimensions)
                    if (occupied.none { it.overlaps(bounds) }) {
                        selected = candidate
                        break@search
                    }
                }
            }
            val target = selected ?: return TablePropLayoutResult.Invalid("NO_SPACE: ${placement.id}")
            occupied += TablePropBounds.of(target, dimensions)
            placed += toWorld(target)
        }
        return TablePropLayoutResult.Success(placed)
    }

    /**
     * 產生中央、邊緣及已占用邊界旁的有限候選位置。
     *
     * @param halfSize 物件在此方向的半尺寸。
     * @param edges 既有物件邊緣旁的候選位置。
     * @return 中央優先、同距離時座標小者優先的合法位置。
     */
    private fun candidates(halfSize: Double, edges: List<Double>): List<Double> = (listOf(0.0, -CENTER_HALF_EXTENT + halfSize, CENTER_HALF_EXTENT - halfSize) + edges)
        .filter { it >= -CENTER_HALF_EXTENT + halfSize && it <= CENTER_HALF_EXTENT - halfSize }
        .distinct().sortedWith(compareBy<Double> { abs(it) }.thenBy { it })

    /**
     * 將世界位置轉回以桌心為原點的局部座標。
     *
     * @param target 世界中的物件目標。
     * @return 尚未受桌子朝向旋轉的目標。
     */
    private fun toLocal(target: TablePropTarget): TablePropTarget {
        val x = target.x - tableLocation.x - 0.5
        val z = target.z - tableLocation.z - 0.5
        val (localX, localZ) = rotate(x, z, inverse = true)
        return target.copy(x = localX, y = target.y - tableLocation.y - 1.0, z = localZ, yaw = target.yaw - facingYaw())
    }

    /**
     * 將局部落點旋轉並平移到世界座標。
     *
     * @param target 以桌心為原點的物件目標。
     * @return 世界中的物件目標。
     */
    private fun toWorld(target: TablePropTarget): TablePropTarget {
        val (x, z) = rotate(target.x, target.z, inverse = false)
        return target.copy(x = tableLocation.x + 0.5 + x, y = tableLocation.y + 1.0 + target.y, z = tableLocation.z + 0.5 + z, yaw = (target.yaw + facingYaw()).mod(360f))
    }

    /**
     * 依桌子朝向進行直角旋轉。
     *
     * @param x 旋轉前的 X 座標。
     * @param z 旋轉前的 Z 座標。
     * @param inverse 是否反向轉回局部座標。
     * @return 旋轉後的水平座標。
     */
    private fun rotate(x: Double, z: Double, inverse: Boolean): Pair<Double, Double> = when (tableFacing) {
        MahjongTableFacing.NORTH -> x to z
        MahjongTableFacing.EAST -> if (inverse) z to -x else -z to x
        MahjongTableFacing.SOUTH -> -x to -z
        MahjongTableFacing.WEST -> if (inverse) -z to x else z to -x
    }

    /** 取得桌子朝向相對於標準北向的轉角。 */
    private fun facingYaw(): Float = when (tableFacing) {
        MahjongTableFacing.NORTH -> 0f
        MahjongTableFacing.EAST -> 90f
        MahjongTableFacing.SOUTH -> 180f
        MahjongTableFacing.WEST -> 270f
    }

    /**
     * 檢查完整命名空間 ID。
     *
     * @param id 待驗證的識別碼。
     * @return 格式有效時為 true。
     */
    private fun validId(id: String): Boolean = try {
        MinecraftResourceIds.requireValid(id) { "Invalid table prop ID: $id" }
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    companion object {
        /** 從四面牌河第一排內緣推導的中央區域半邊長。 */
        const val CENTER_HALF_EXTENT = MahjongTileTableLayout.DISCARD_ROW_BASE_OFFSET - MahjongTileDimensions.TILE_HEIGHT / 2.0

        /** 中央物件邊緣之間的留白。 */
        private const val CENTER_GAP = 0.01

        /** 單次呈現處理的物件數保護上限，不代表可放置容量。 */
        private const val MAX_PLACEMENTS = 256

        /** 每一物件可嘗試的配置候選數保護上限。 */
        private const val MAX_CENTER_CANDIDATES = 4096
    }
}
