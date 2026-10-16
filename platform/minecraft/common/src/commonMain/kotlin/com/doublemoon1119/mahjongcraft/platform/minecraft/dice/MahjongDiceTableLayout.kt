package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import kotlin.uuid.Uuid

/**
 * 將座位索引換算成桌子的局部側面，供玩家站位、牌面方向與骰子投入側共用。
 *
 * 索引 0、1、2、3 依序對應南、東、北、西，使 [TableState.getNextPlayer] 的下家位於目前玩家右側。
 * 這是玩家的座位順序，不是 [MahjongTileTableLayout] 組裝牌牆時使用的環狀順序；兩者不可互相替代。
 *
 * @param seatIndex 回合順序中的座位索引；會依四席循環取餘數。
 * @return 對應的桌子局部側面。
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
    /** 桌面上有預先驗證擺放位置的骰子數量；規則一次擲出其他數量時無法呈現。 */
    val supportedDiceCounts: Set<Int> = setOf(2, 3)

    /**
     * 依桌子 UUID、投擲序號、局部投入側與世界朝向取得安全呈現位置。
     *
     * 基準 variant 皆由桌子局部南側投入。桌子 UUID 決定四組 variant 的固定排列，投擲序號依序輪替；
     * 之後先旋轉至 [throwSide]，再依 [tableFacing] 旋轉至世界方向。桌面高度固定為 controller 的
     * `Y + 1.0`。
     *
     * @param controllerX 桌子 controller 的方塊 X 座標。
     * @param controllerY 桌子 controller 的方塊 Y 座標。
     * @param controllerZ 桌子 controller 的方塊 Z 座標。
     * @param tableId 用來穩定選擇 variant 起點與輪替方向的桌子 UUID。
     * @param tableFacing 桌子在世界中的水平朝向。
     * @param throwSide 擲骰者所在的桌子局部側面。
     * @param rollSequence 此桌目前的投擲序號。
     * @param diceCount 本次擲骰的數量，只支援 2 或 3 顆。
     * @return 依骰子擲出順序排列的桌面位置。
     * @throws IllegalArgumentException 骰子數量不是 2 或 3 時。
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
     * （= 這個值 + [DiceRollAnimationSpec.DEFAULT_DURATION_TICKS]）。
     *
     * @param diceCount 本次擲骰數量，只支援 2 或 3 顆。
     * @return 最後一顆骰子的起始延遲 tick 數。
     * @throws IllegalArgumentException 骰子數量不是 2 或 3 時。
     */
    fun maxStartDelayTicks(diceCount: Int): Int = variantsFor(diceCount).first().maxOf { it.startDelayTicks }

    /**
     * 依骰子數量取得從「呼叫端觸發擲骰呈現」到「整組擲骰動畫（含額外觀看時間）全部播完」所需的總
     * tick 數。單一來源，供 [maxStartDelayTicks] 呼叫端計算忙碌時長，以及任何需要「等骰子動畫播完
     * 才執行」的後續動作（例如牌牆事後接線）共用，不各自重複組合這三個數字。
     *
     * @param diceCount 本次擲骰數量，只支援 2 或 3 顆。
     * @return 從觸發呈現到所有骰子及結果展示結束的 tick 數。
     * @throws IllegalArgumentException 骰子數量不是 2 或 3 時。
     */
    fun totalAnimationTicks(diceCount: Int): Int = maxStartDelayTicks(diceCount) +
        DiceRollAnimationSpec.DEFAULT_DURATION_TICKS +
        DiceRollAnimationSpec.EXTRA_VIEWING_TICKS

    /**
     * 依骰子數量選擇已驗證的基準 variant。
     *
     * @param diceCount 本次擲骰數量。
     * @return 對應數量的預先驗證位置組合。
     * @throws IllegalArgumentException 不支援該骰子數量時。
     */
    private fun variantsFor(diceCount: Int): List<List<MahjongDiceTablePlacement>> = when (diceCount) {
        2 -> TWO_DICE_VARIANTS
        3 -> THREE_DICE_VARIANTS
        else -> throw IllegalArgumentException("Dice table layout supports two or three dice")
    }

    /**
     * 使用跨平台穩定的 UUID 字串雜湊決定 variant 起點及正向／反向輪替。
     *
     * @param tableId 用來決定 variant 順序的桌子 UUID。
     * @param rollSequence 同桌遞增的投擲序號。
     * @param variantCount 可選 variant 數量。
     * @return 此桌此序號使用的 variant 索引。
     */
    private fun variantIndex(tableId: Uuid, rollSequence: Long, variantCount: Int): Int {
        val hash = tableId.toString().fold(STABLE_HASH_OFFSET) { value, character ->
            (value xor character.code.toLong()) * STABLE_HASH_MULTIPLIER
        }
        val start = floorMod(hash, variantCount)
        val step = if ((hash ushr 1) and 1L == 0L) 1 else variantCount - 1
        return floorMod(start.toLong() + rollSequence * step, variantCount)
    }

    /**
     * 將基準南側向量旋轉至局部投入側，再跟著桌子旋轉至世界朝向。
     *
     * @param vector 以局部南側為基準的向量。
     * @param throwSide 向量要旋轉至的投入側。
     * @param tableFacing 桌子在世界中的朝向。
     * @return 世界座標方向的向量。
     */
    private fun rotateToWorld(
        vector: DiceAnimationVector,
        throwSide: MahjongTableSide,
        tableFacing: MahjongTableFacing,
    ): DiceAnimationVector = rotateForFacing(rotateForSide(vector, throwSide), tableFacing)

    /**
     * 將局部南側基準旋轉至指定投入側。
     *
     * @param vector 以局部南側為基準的向量。
     * @param side 要旋轉至的桌面側面。
     * @return 以指定側面為基準的向量。
     */
    private fun rotateForSide(vector: DiceAnimationVector, side: MahjongTableSide): DiceAnimationVector = when (side) {
        MahjongTableSide.SOUTH -> vector
        MahjongTableSide.WEST -> DiceAnimationVector(-vector.z, vector.y, vector.x)
        MahjongTableSide.NORTH -> DiceAnimationVector(-vector.x, vector.y, -vector.z)
        MahjongTableSide.EAST -> DiceAnimationVector(vector.z, vector.y, -vector.x)
    }

    /**
     * 將朝北擺放桌子的局部向量旋轉至指定世界朝向。
     *
     * @param vector 以桌子局部方向為基準的向量。
     * @param facing 桌子在世界中的朝向。
     * @return 世界座標方向的向量。
     */
    private fun rotateForFacing(vector: DiceAnimationVector, facing: MahjongTableFacing): DiceAnimationVector = when (facing) {
        MahjongTableFacing.NORTH -> vector
        MahjongTableFacing.EAST -> DiceAnimationVector(-vector.z, vector.y, vector.x)
        MahjongTableFacing.SOUTH -> DiceAnimationVector(-vector.x, vector.y, -vector.z)
        MahjongTableFacing.WEST -> DiceAnimationVector(vector.z, vector.y, -vector.x)
    }

    /**
     * 對負數保持非負結果的餘數計算。
     *
     * @param value 要取餘數的值。
     * @param divisor 正除數。
     * @return 範圍在 0（含）至除數（不含）之間的餘數。
     */
    private fun floorMod(value: Long, divisor: Int): Int = ((value % divisor) + divisor).toInt() % divisor

    /**
     * 建立單顆基準位置。
     *
     * @param finalX 最終位置的 X 偏移。
     * @param finalZ 最終位置的 Z 偏移。
     * @param startX 起點相對最終位置的 X 偏移。
     * @param startY 起點相對最終位置的 Y 偏移。
     * @param startZ 起點相對最終位置的 Z 偏移。
     * @param delay 動畫開始延遲 tick 數。
     * @return 指定最終位置、起點與延遲的骰子呈現位置。
     */
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

    /** 方塊中心相對整數座標的偏移。 */
    private const val BLOCK_CENTER: Double = 0.5

    /** 桌面相對 controller 方塊底部的高度。 */
    private const val TABLETOP_HEIGHT: Double = 1.0

    /** UUID 字串穩定雜湊的初始值。 */
    private const val STABLE_HASH_OFFSET: Long = -3750763034362895579L

    /** UUID 字串每一步雜湊使用的乘數。 */
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
        listOf(
            placement(-0.25, -0.10, -0.22, 0.64, 0.74, 0),
            placement(0.02, 0.18, 0.02, 0.70, 0.80, 2),
            placement(0.27, -0.08, 0.24, 0.66, 0.76, 4),
        ),
        listOf(
            placement(-0.26, 0.16, -0.25, 0.69, 0.79, 0),
            placement(-0.02, -0.16, 0.00, 0.63, 0.72, 2),
            placement(0.25, 0.11, 0.23, 0.67, 0.77, 4),
        ),
        listOf(
            placement(-0.29, -0.02, -0.18, 0.66, 0.76, 0),
            placement(0.00, 0.22, 0.04, 0.71, 0.81, 2),
            placement(0.23, -0.18, 0.27, 0.62, 0.71, 4),
        ),
        listOf(
            placement(-0.20, -0.20, -0.27, 0.63, 0.73, 0),
            placement(-0.03, 0.12, 0.00, 0.68, 0.78, 2),
            placement(0.28, 0.00, 0.25, 0.70, 0.80, 4),
        ),
    )
}
