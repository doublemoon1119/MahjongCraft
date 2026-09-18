package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import kotlin.math.abs
import kotlin.uuid.Uuid

/**
 * 一張桌子此刻應有的全部規則桌面物件。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property placements 規則描述的完整物件清單；空清單代表桌上不應有任何規則桌面物件。
 */
data class TablePropPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val placements: List<TablePropPlacement>,
) {
    /** 依桌子位置與朝向，把每個座位本地描述換算成世界落點，順序與 [placements] 相同。 */
    fun targets(): List<TablePropTarget> = placements.map { placement ->
        val world = MahjongTileTableLayout.seatAnchorPlacement(
            controllerX = tableLocation.x,
            controllerY = tableLocation.y,
            controllerZ = tableLocation.z,
            tableFacing = tableFacing,
            seatIndex = placement.seatIndex,
            anchor = placement.anchor,
            offset = placement.offset,
            yawOffset = placement.yawOffset,
        )
        TablePropTarget(
            kind = placement.kind,
            variant = placement.variant,
            x = world.x,
            y = world.y,
            z = world.z,
            yaw = world.yaw,
        )
    }
}

/** 規則桌面物件呈現請求的處理結果。 */
enum class TablePropPresentationResult {
    /** 桌上的規則桌面物件已更新為 [TablePropPresentation.placements] 描述的樣子。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 生成新物件失敗；這次已生成的部分會被移除，桌上原有的物件維持不變。 */
    SPAWN_FAILED,
}

/**
 * 將規則描述的桌面物件同步到 Minecraft 世界的版本 adapter 邊界。
 *
 * 採差量同步：種類、變體、落點與朝向都相同的既有物件原樣保留，只替缺少的落點生成新物件，新物件全部生成
 * 成功後才移除多出來的舊物件。
 */
interface TablePropPresenter {
    /** 把指定桌子的規則桌面物件同步為 [presentation] 描述的完整清單。 */
    fun present(presentation: TablePropPresentation): TablePropPresentationResult

    /** 清除指定桌子目前所有的規則桌面物件；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}

/**
 * 一個規則桌面物件在世界中的身分與落點。
 *
 * @property kind 物件種類識別碼。
 * @property variant 種類內的變體。
 * @property x 世界 X 座標。
 * @property y 世界 Y 座標。
 * @property z 世界 Z 座標。
 * @property yaw 水平朝向（度）。
 */
data class TablePropTarget(
    val kind: String,
    val variant: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
) {
    /** 種類與變體相同，且座標與朝向落在容差內時視為同一個物件。 */
    fun matches(other: TablePropTarget): Boolean = kind == other.kind &&
        variant == other.variant &&
        abs(x - other.x) <= POSITION_EPSILON &&
        abs(y - other.y) <= POSITION_EPSILON &&
        abs(z - other.z) <= POSITION_EPSILON &&
        yawDistance(yaw, other.yaw) <= YAW_EPSILON

    private companion object {
        /** 世界座標比對容差；足以吸收 entity 座標的浮點往返誤差，又不會混淆相鄰落點。 */
        const val POSITION_EPSILON: Double = 1.0e-4

        /** 水平朝向比對容差（度）。 */
        const val YAW_EPSILON: Float = 1.0e-3f

        /** 一圈角度。 */
        const val FULL_ROTATION_DEGREES: Float = 360.0f

        /** 環狀角度差，讓 `180` 與 `-180` 視為同一朝向。 */
        fun yawDistance(first: Float, second: Float): Float {
            val difference = abs((first - second) % FULL_ROTATION_DEGREES)
            return minOf(difference, FULL_ROTATION_DEGREES - difference)
        }
    }
}

/**
 * 差量同步的結果。
 *
 * @property missing 桌上還沒有、需要生成的落點。
 * @property surplus 不在目標清單中、需要移除的既有物件。
 */
data class TablePropSyncPlan<T>(
    val missing: List<TablePropTarget>,
    val surplus: List<T>,
)

/**
 * 比對目標清單與桌上既有物件。
 *
 * 每個既有物件最多對應一個目標；無法辨識身分（[targetOf] 回傳 null）的既有物件一律視為多餘。
 *
 * @param targets 桌上應有的全部物件落點。
 * @param existing 桌上目前由這張桌子管理的物件。
 * @param targetOf 讀出既有物件目前的身分與落點。
 */
fun <T> planTablePropSync(
    targets: List<TablePropTarget>,
    existing: List<T>,
    targetOf: (T) -> TablePropTarget?,
): TablePropSyncPlan<T> {
    val unmatched = existing.mapNotNull { entry -> targetOf(entry)?.let { entry to it } }.toMutableList()
    val unidentified = existing.filter { entry -> targetOf(entry) == null }
    val missing = targets.filter { target ->
        val index = unmatched.indexOfFirst { (_, current) -> target.matches(current) }
        if (index >= 0) {
            unmatched.removeAt(index)
            false
        } else {
            true
        }
    }
    return TablePropSyncPlan(
        missing = missing,
        surplus = unidentified + unmatched.map { (entry, _) -> entry },
    )
}
