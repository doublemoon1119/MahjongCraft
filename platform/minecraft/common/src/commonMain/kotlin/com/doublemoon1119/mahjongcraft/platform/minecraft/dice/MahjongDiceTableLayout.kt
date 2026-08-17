package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlin.uuid.Uuid

/** 麻將桌 controller 的世界水平朝向；避免共用 layout 依賴特定 Minecraft 版本的 Direction。 */
enum class MahjongTableFacing {
    /** 朝向世界北方。 */
    NORTH,

    /** 朝向世界東方。 */
    EAST,

    /** 朝向世界南方。 */
    SOUTH,

    /** 朝向世界西方。 */
    WEST,
}

/** 擲骰者相對麻將桌的局部側面；不受桌子在世界中的朝向影響。 */
enum class MahjongTableSide {
    /** 麻將桌局部北側。 */
    NORTH,

    /** 麻將桌局部東側。 */
    EAST,

    /** 麻將桌局部南側。 */
    SOUTH,

    /** 麻將桌局部西側。 */
    WEST,
}

/**
 * 正式擲骰時單顆骰子的版本無關呈現位置。
 *
 * @property finalPosition 動畫結束後骰子底部中心的世界座標。
 * @property startOffset 動畫起點相對 [finalPosition] 的向量。
 * @property startDelayTicks 相對同次擲骰開始時間的延遲 tick 數。
 */
data class MahjongDiceTablePlacement(
    val finalPosition: DiceAnimationVector,
    val startOffset: DiceAnimationVector,
    val startDelayTicks: Int,
)

/**
 * 把座位 index 換算成桌子局部側面：index 0 固定對應局部南側，之後依序旋轉。任何需要「某位座位玩家的
 * 局部側面」的呈現層計算（骰子投入側、座位站立位置、手牌／摸牌位／牌河座位方向、[wallPlacement][
 * com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout.wallPlacement] 的
 * `dealerSeatIndex` 錨點）都共用這個函式，不各自重複定義。
 *
 * 這裡採用 SOUTH→EAST→NORTH→WEST（順時針），刻意跟
 * [MahjongTileTableLayout][com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout]
 * 內部 `SIDE_ORDER`／`advance`／`localWallVector` 使用的 SOUTH→WEST→NORTH→EAST（逆時針）**不是同一個
 * 方向**——這兩者過去被誤以為必須共用同一套旋轉順序，因而耦合修改了三次，三次都在牌牆密合度或墩的
 * 左右手方向上被使用者實際遊玩截圖推翻（詳見專案記憶 `seat-direction-ccw-fix`）。實際上這是兩個各自
 * 獨立的問題：
 *
 * - **牌牆自身是否密合、墩的左右手方向是否正確**：只取決於 [MahjongTileTableLayout] 內部
 *   `SIDE_ORDER`／`advance`／`localWallVector` 三者的相對關係，屬於牌牆環狀結構本身的幾何自洽性，
 *   跟這裡的 `seatIndexToTableSide` 選哪個方向、從哪個側面開始編號完全無關（`advance` 只是把
 *   `SIDE_ORDER` 這個固定環從任意起點開始走，起點在哪裡不影響環本身密不密合、左右手對不對）。
 * - **玩家（連同手牌／摸牌位／牌河，因為這些本來就該跟著玩家站的位置走）該站在哪個物理側面**：只取決於
 *   這裡的 `seatIndexToTableSide`，需要滿足「回合順序中的下一位玩家（[TableState.getNextPlayer][
 *   com.doublemoon1119.mahjongcraft.logic.table.TableState.getNextPlayer]，即下家）物理站位在目前
 *   玩家的右手邊」這個真實麻將慣例，這件事跟牌牆內部怎麼組裝完全無關。
 *
 * 因此這裡改成順時針（`seatIndex + 1` 落在右手邊），[MahjongTileTableLayout] 的 `SIDE_ORDER`／
 * `advance`／`localWallVector` 完全不動、繼續維持已證實正確的逆時針版本——兩邊各自獨立正確，不需要
 * 也不應該再耦合修改。
 */
fun seatIndexToTableSide(seatIndex: Int): MahjongTableSide = when (seatIndex.mod(SEAT_COUNT)) {
    0 -> MahjongTableSide.SOUTH
    1 -> MahjongTableSide.EAST
    2 -> MahjongTableSide.NORTH
    else -> MahjongTableSide.WEST
}

/** 麻將桌固定座位數。 */
private const val SEAT_COUNT: Int = 4

/** 兩款 3×3 麻將桌共用的兩顆／三顆正式骰子位置計算。 */
object MahjongDiceTableLayout {
    /**
     * 依桌子 UUID、投擲序號、局部投入側與世界朝向取得安全呈現位置。
     *
     * 基準 variant 皆由桌子局部南側投入。桌子 UUID 決定四組 variant 的固定排列，投擲序號依序輪替；
     * 之後先旋轉至 [throwSide]，再依 [tableFacing] 旋轉至世界方向。桌面高度固定為 controller 的
     * `Y + 1.0`。
     */
    fun placements(
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableId: Uuid,
        tableFacing: MahjongTableFacing,
        throwSide: MahjongTableSide,
        rollSequence: Long,
        diceCount: Int,
    ): List<MahjongDiceTablePlacement> {
        val variants = variantsFor(diceCount)
        val variant = variants[variantIndex(tableId, rollSequence, variants.size)]
        return variant.map { local ->
            val finalOffset = rotateToWorld(local.finalPosition, throwSide, tableFacing)
            MahjongDiceTablePlacement(
                finalPosition = DiceAnimationVector(
                    x = controllerX + BLOCK_CENTER + finalOffset.x,
                    y = controllerY + TABLETOP_HEIGHT,
                    z = controllerZ + BLOCK_CENTER + finalOffset.z,
                ),
                startOffset = rotateToWorld(local.startOffset, throwSide, tableFacing),
                startDelayTicks = local.startDelayTicks,
            )
        }
    }

    /**
     * 依骰子數量取得該組 variant 內最大的 [MahjongDiceTablePlacement.startDelayTicks]——所有 variant
     * 對同一顆骰子（依丟出順序）的延遲皆相同，只有落點座標不同，取任一組 variant 即可。用於呼叫端
     * 計算「最後一顆骰子開始動畫」到「整組擲骰動畫全部結束」所需的總 tick 數
     * （= 這個值 + [com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimationSpec.DEFAULT_DURATION_TICKS]）。
     */
    fun maxStartDelayTicks(diceCount: Int): Int = variantsFor(diceCount).first().maxOf { it.startDelayTicks }

    /**
     * 依骰子數量取得從「呼叫端觸發擲骰呈現」到「整組擲骰動畫（含額外觀看時間）全部播完」所需的總
     * tick 數。單一來源，供 [maxStartDelayTicks] 呼叫端計算忙碌時長，以及任何需要「等骰子動畫播完
     * 才執行」的後續動作（例如牌牆事後接線）共用，不各自重複組合這三個數字。
     */
    fun totalAnimationTicks(diceCount: Int): Int = maxStartDelayTicks(diceCount) +
        DiceRollAnimationSpec.DEFAULT_DURATION_TICKS +
        DiceRollAnimationSpec.EXTRA_VIEWING_TICKS

    /** 依骰子數量選擇已驗證的基準 variant。 */
    private fun variantsFor(diceCount: Int): List<List<MahjongDiceTablePlacement>> = when (diceCount) {
        2 -> TWO_DICE_VARIANTS
        3 -> THREE_DICE_VARIANTS
        else -> throw IllegalArgumentException("Dice table layout supports two or three dice")
    }

    /** 使用跨平台穩定的 UUID 字串雜湊決定 variant 起點及正向／反向輪替。 */
    private fun variantIndex(tableId: Uuid, rollSequence: Long, variantCount: Int): Int {
        val hash = tableId.toString().fold(STABLE_HASH_OFFSET) { value, character ->
            (value xor character.code.toLong()) * STABLE_HASH_MULTIPLIER
        }
        val start = floorMod(hash, variantCount)
        val step = if ((hash ushr 1) and 1L == 0L) 1 else variantCount - 1
        return floorMod(start.toLong() + rollSequence * step, variantCount)
    }

    /** 將基準南側向量旋轉至局部投入側，再跟著桌子旋轉至世界朝向。 */
    private fun rotateToWorld(
        vector: DiceAnimationVector,
        throwSide: MahjongTableSide,
        tableFacing: MahjongTableFacing,
    ): DiceAnimationVector = rotateForFacing(rotateForSide(vector, throwSide), tableFacing)

    /** 將局部南側基準旋轉至指定投入側。 */
    private fun rotateForSide(vector: DiceAnimationVector, side: MahjongTableSide): DiceAnimationVector = when (side) {
        MahjongTableSide.SOUTH -> vector
        MahjongTableSide.WEST -> DiceAnimationVector(-vector.z, vector.y, vector.x)
        MahjongTableSide.NORTH -> DiceAnimationVector(-vector.x, vector.y, -vector.z)
        MahjongTableSide.EAST -> DiceAnimationVector(vector.z, vector.y, -vector.x)
    }

    /** 將朝北擺放桌子的局部向量旋轉至指定世界朝向。 */
    private fun rotateForFacing(vector: DiceAnimationVector, facing: MahjongTableFacing): DiceAnimationVector = when (facing) {
        MahjongTableFacing.NORTH -> vector
        MahjongTableFacing.EAST -> DiceAnimationVector(-vector.z, vector.y, vector.x)
        MahjongTableFacing.SOUTH -> DiceAnimationVector(-vector.x, vector.y, -vector.z)
        MahjongTableFacing.WEST -> DiceAnimationVector(vector.z, vector.y, -vector.x)
    }

    /** 對負數保持非負結果的餘數計算。 */
    private fun floorMod(value: Long, divisor: Int): Int = ((value % divisor) + divisor).toInt() % divisor

    /** 建立單顆基準位置。 */
    private fun placement(
        finalX: Double,
        finalZ: Double,
        startX: Double,
        startY: Double,
        startZ: Double,
        delay: Int,
    ): MahjongDiceTablePlacement = MahjongDiceTablePlacement(
        finalPosition = DiceAnimationVector(finalX, 0.0, finalZ),
        startOffset = DiceAnimationVector(startX, startY, startZ),
        startDelayTicks = delay,
    )

    /** 固定桌面幾何與 UUID 雜湊常數。 */
    private const val BLOCK_CENTER: Double = 0.5
    private const val TABLETOP_HEIGHT: Double = 1.0
    private const val STABLE_HASH_OFFSET: Long = -3750763034362895579L
    private const val STABLE_HASH_MULTIPLIER: Long = 1099511628211L

    /** 由局部南側投入的四組雙骰安全位置。 */
    private val TWO_DICE_VARIANTS: List<List<MahjongDiceTablePlacement>> = listOf(
        listOf(placement(-0.22, -0.06, -0.18, 0.62, 0.72, 0), placement(0.22, 0.06, 0.18, 0.68, 0.78, 2)),
        listOf(placement(-0.12, 0.18, -0.28, 0.66, 0.76, 0), placement(0.25, -0.13, 0.12, 0.61, 0.70, 2)),
        listOf(placement(-0.28, 0.10, -0.08, 0.70, 0.80, 0), placement(0.12, -0.19, 0.25, 0.64, 0.73, 2)),
        listOf(placement(-0.08, -0.23, -0.24, 0.63, 0.74, 0), placement(0.27, 0.15, 0.16, 0.69, 0.79, 2)),
    )

    /** 由局部南側投入的四組三骰安全位置。 */
    private val THREE_DICE_VARIANTS: List<List<MahjongDiceTablePlacement>> = listOf(
        listOf(placement(-0.25, -0.10, -0.22, 0.64, 0.74, 0), placement(0.02, 0.18, 0.02, 0.70, 0.80, 2), placement(0.27, -0.08, 0.24, 0.66, 0.76, 4)),
        listOf(placement(-0.26, 0.16, -0.25, 0.69, 0.79, 0), placement(-0.02, -0.16, 0.00, 0.63, 0.72, 2), placement(0.25, 0.11, 0.23, 0.67, 0.77, 4)),
        listOf(placement(-0.29, -0.02, -0.18, 0.66, 0.76, 0), placement(0.00, 0.22, 0.04, 0.71, 0.81, 2), placement(0.23, -0.18, 0.27, 0.62, 0.71, 4)),
        listOf(placement(-0.20, -0.20, -0.27, 0.63, 0.73, 0), placement(-0.03, 0.12, 0.00, 0.68, 0.78, 2), placement(0.28, 0.00, 0.25, 0.70, 0.80, 4)),
    )
}
