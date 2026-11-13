package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * 散放牌河的位置，見 [TableDiscardArea.SCATTERED]。座標以桌子為準：X 往南側座位的右手邊為正，Z 往南側座位為正。
 *
 * 每一局從 [PATTERN_SEEDS] 挑一張位置表：位置表事先在牌牆內側、擲骰區之外排滿互不重疊的位置，每個位置有自己的角度。
 * 打出的牌依全桌打牌順序逐張挑一個空位：優先在打牌者那一側的空位中隨機挑，那一側滿了才挑最靠近打牌者的空位。
 * 只有剛打出的牌能被鳴走，下一張打出前就已確定，所以被鳴走的牌讓出的位置可以給之後打出的牌使用；牌河上同時存在的牌
 * 不超過活牌張數加一。一張牌的位置只取決於它自己與比它先打出的牌，之後打出的牌與鳴牌都不會讓它移動。
 */
internal object ScatteredDiscardLayout {
    /**
     * 依全桌打牌順序為每張牌挑位置。
     *
     * @param slots 這一局的位置表。
     * @param discards 依打出先後排列的牌、打牌者所在方向的單位向量 (x, z) 與是否已被鳴走；包含被鳴走的牌。
     * @return 每張牌的位置與角度；位置表沒有空位時疊到上一層，內建規則的牌數不會用完位置表。
     */
    fun assign(slots: List<ScatteredDiscard>, discards: List<ScatterRequest>): Map<Uuid, ScatteredDiscard> {
        val used = BooleanArray(slots.size)
        return discards.withIndex().associate { (order, discard) ->
            val (tileId, towardX, towardZ, isTaken) = discard
            fun facing(index: Int) = slots[index].x * towardX + slots[index].z * towardZ
            val free = slots.indices.filterNot { used[it] }
            val index = if (free.isEmpty()) {
                null
            } else {
                val ownSide = free.filter { facing(it) > 0.0 }
                if (ownSide.isNotEmpty()) {
                    ownSide[tileId.toLongs { most, least -> Random(most xor least) }.nextInt(ownSide.size)]
                } else {
                    free.maxBy(::facing)
                }
            }
            tileId to if (index == null) {
                val reused = slots[order % slots.size]
                reused.copy(y = (order / slots.size + 1) * LAYER_HEIGHT)
            } else {
                used[index] = !isTaken
                slots[index]
            }
        }
    }

    /**
     * 這一局使用的位置表，同一張桌子相鄰兩局一定不同。
     *
     * @param tableId 桌子。
     * @param roundSerial 本場第幾局。
     * @param reach 位置離桌心最遠的距離，沿 X 與 Z 各自計算。
     * @return 位置表。
     */
    fun roundPattern(tableId: Uuid, roundSerial: Int, reach: Double): List<ScatteredDiscard> {
        val tableOffset = tableId.toLongs { most, least -> (most xor least).mod(PATTERN_SEEDS.size.toLong()).toInt() }
        return pattern(PATTERN_SEEDS[(tableOffset + roundSerial).mod(PATTERN_SEEDS.size)], reach)
    }

    /**
     * 用 [seed] 排出位置表；同一個種子與範圍只排一次。
     *
     * @param seed 種子。
     * @param reach 位置離桌心最遠的距離。
     * @return 互不重疊、都在範圍內的位置。
     */
    fun pattern(seed: Int, reach: Double): List<ScatteredDiscard> {
        val key = PatternKey(seed, reach)
        patterns[key]?.let { return it }
        return generate(seed, reach).also { patterns = patterns + (key to it) }
    }

    /**
     * 排出位置表：隨機挑位置與角度，一半的位置貼著已經排好的位置找，讓位置自然聚在一起，也比較不會留下放不進牌的零碎空隙；
     * 連續 [GENERATION_PATIENCE] 次都放不下時結束。
     *
     * @param seed 種子。
     * @param reach 範圍外緣。
     * @return 位置表。
     */
    private fun generate(seed: Int, reach: Double): List<ScatteredDiscard> {
        val random = Random(seed)
        val slots = mutableListOf<ScatteredDiscard>()
        var failures = 0
        while (failures < GENERATION_PATIENCE) {
            val neighbour = slots.takeIf { it.isNotEmpty() && random.nextDouble() < NEIGHBOUR_CHANCE }?.let { it[random.nextInt(it.size)] }
            val candidate = if (neighbour == null) {
                ScatteredDiscard(
                    x = random.nextDouble(-reach, reach),
                    y = 0.0,
                    z = random.nextDouble(-reach, reach),
                    angleDegrees = random.nextDouble(0.0, FULL_TURN_DEGREES).toFloat(),
                )
            } else {
                besideNeighbour(neighbour, random, NEIGHBOUR_SPACING, NEIGHBOUR_TURN_DEGREES)
            }
            if (fits(candidate, reach) && slots.none { overlaps(it, candidate) }) {
                slots += candidate
                failures = 0
            } else {
                failures++
            }
        }
        // 補空隙：只貼著已排好的位置找，間隔與角度差都更小，把剩下放得進牌的縫填滿。
        failures = 0
        while (slots.isNotEmpty() && failures < INFILL_PATIENCE) {
            val candidate = besideNeighbour(slots[random.nextInt(slots.size)], random, INFILL_SPACING, INFILL_TURN_DEGREES)
            if (fits(candidate, reach) && slots.none { overlaps(it, candidate) }) {
                slots += candidate
                failures = 0
            } else {
                failures++
            }
        }
        return slots
    }

    /**
     * 貼在 [neighbour] 側邊或兩端的位置，角度與它相差一點，讓牌大致順著排、空隙比較少。
     *
     * @param neighbour 已排好的位置。
     * @param random 排位置表用的隨機序列。
     * @param maxSpacing 與鄰居之間多留的最大空隙。
     * @param maxTurnDegrees 與鄰居角度相差的最大值。
     * @return 候選位置。
     */
    private fun besideNeighbour(
        neighbour: ScatteredDiscard,
        random: Random,
        maxSpacing: Double,
        maxTurnDegrees: Double,
    ): ScatteredDiscard {
        val angle = neighbour.angleDegrees * PI / 180.0
        val sideways = random.nextBoolean()
        val sign = if (random.nextBoolean()) 1.0 else -1.0
        val spacing = random.nextDouble(0.0, maxSpacing)
        // 在鄰居自己的座標系裡往側邊（寬邊方向）或兩端（長邊方向）移開一張牌的距離，再隨機沿另一個方向錯開一點。
        val along = sign * if (sideways) MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING + spacing else 0.0
        val across = sign * if (sideways) 0.0 else MahjongTileDimensions.TILE_HEIGHT + MahjongTileDimensions.TILE_SMALL_PADDING + spacing
        val slide = random.nextDouble(-NEIGHBOUR_SLIDE, NEIGHBOUR_SLIDE)
        val localX = along + if (sideways) 0.0 else slide
        val localZ = across + if (sideways) slide else 0.0
        return ScatteredDiscard(
            x = neighbour.x + localX * cos(angle) - localZ * sin(angle),
            y = 0.0,
            z = neighbour.z + localX * sin(angle) + localZ * cos(angle),
            angleDegrees = (neighbour.angleDegrees + random.nextDouble(-maxTurnDegrees, maxTurnDegrees).toFloat()).mod(FULL_TURN_DEGREES.toFloat()),
        )
    }

    /**
     * 牌整張落在牌河範圍內：四個角都在牌牆內側，整張牌都在擲骰區之外。
     *
     * @param discard 候選位置。
     * @param reach 範圍外緣。
     * @return 放得下時為 true。
     */
    internal fun fits(discard: ScatteredDiscard, reach: Double): Boolean = corners(discard).all { (x, z) -> abs(x) <= reach && abs(z) <= reach } &&
        distanceFromCentre(discard) >= MahjongTileTableLayout.SCATTER_DICE_CLEARANCE

    /**
     * 兩張牌是否重疊，以實際旋轉後的長方形判斷，並保留一點縫隙。
     *
     * @param first 第一張。
     * @param second 第二張。
     * @return 重疊或靠得比縫隙還近時為 true。
     */
    internal fun overlaps(first: ScatteredDiscard, second: ScatteredDiscard): Boolean {
        if (hypot(first.x - second.x, first.z - second.z) > 2.0 * PADDED_HALF_DIAGONAL) return false
        val firstCorners = corners(first, MahjongTileDimensions.TILE_SMALL_PADDING / 2.0)
        val secondCorners = corners(second, MahjongTileDimensions.TILE_SMALL_PADDING / 2.0)
        return (axes(first) + axes(second)).none { (ax, az) ->
            val firstRange = firstCorners.map { (x, z) -> x * ax + z * az }
            val secondRange = secondCorners.map { (x, z) -> x * ax + z * az }
            firstRange.max() < secondRange.min() || secondRange.max() < firstRange.min()
        }
    }

    /**
     * 牌旋轉後的四個角；牌在角度 `0` 時寬邊沿 X。
     *
     * @param discard 牌的位置與角度。
     * @param padding 每邊往外加的距離。
     * @return 四個角的 (x, z)。
     */
    internal fun corners(discard: ScatteredDiscard, padding: Double = 0.0): List<Pair<Double, Double>> {
        val angle = discard.angleDegrees * PI / 180.0
        val halfWidth = MahjongTileDimensions.TILE_WIDTH / 2.0 + padding
        val halfHeight = MahjongTileDimensions.TILE_HEIGHT / 2.0 + padding
        return listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0).map { (sx, sz) ->
            val dx = sx * halfWidth
            val dz = sz * halfHeight
            Pair(discard.x + dx * cos(angle) - dz * sin(angle), discard.z + dx * sin(angle) + dz * cos(angle))
        }
    }

    /**
     * 牌的兩條邊方向，供分離軸檢查。
     *
     * @param discard 牌。
     * @return 兩條單位向量。
     */
    private fun axes(discard: ScatteredDiscard): List<Pair<Double, Double>> {
        val angle = discard.angleDegrees * PI / 180.0
        return listOf(cos(angle) to sin(angle), -sin(angle) to cos(angle))
    }

    /**
     * 桌心到牌上最近一點的距離。
     *
     * @param discard 牌。
     * @return 距離；桌心在牌上時為 `0`。
     */
    internal fun distanceFromCentre(discard: ScatteredDiscard): Double {
        val angle = discard.angleDegrees * PI / 180.0
        // 把桌心換到牌自己的座標系，再找牌上最近的一點。
        val localX = -discard.x * cos(angle) - discard.z * sin(angle)
        val localZ = discard.x * sin(angle) - discard.z * cos(angle)
        val nearestX = localX.coerceIn(-MahjongTileDimensions.TILE_WIDTH / 2.0, MahjongTileDimensions.TILE_WIDTH / 2.0)
        val nearestZ = localZ.coerceIn(-MahjongTileDimensions.TILE_HEIGHT / 2.0, MahjongTileDimensions.TILE_HEIGHT / 2.0)
        return hypot(localX - nearestX, localZ - nearestZ)
    }

    /**
     * 已排出的位置表，依種子與範圍索引。整份換成新的對照表，不修改已發布的內容；同時排出同一張位置表時只是重複計算。
     */
    @Volatile
    private var patterns: Map<PatternKey, List<ScatteredDiscard>> = emptyMap()

    /**
     * 位置表的索引。
     *
     * @property seed 種子。
     * @property reach 範圍外緣。
     */
    private data class PatternKey(val seed: Int, val reach: Double)

    /** 每一局從中挑一張位置表的種子；每一張都由測試確認位置數多於內建規則牌河上同時可能存在的牌數。 */
    internal val PATTERN_SEEDS: List<Int> = listOf(12, 29, 58, 136, 164, 221, 291, 296)

    /** 排位置表時連續放不下幾次就結束。 */
    private const val GENERATION_PATIENCE: Int = 4_000

    /** 貼著已排好的位置找的機率。 */
    private const val NEIGHBOUR_CHANCE: Double = 0.7

    /** 貼著排時與鄰居之間多留的最大空隙。 */
    private const val NEIGHBOUR_SPACING: Double = MahjongTileDimensions.TILE_WIDTH * 0.25

    /** 貼著排時沿鄰居的邊錯開的最大距離。 */
    private const val NEIGHBOUR_SLIDE: Double = MahjongTileDimensions.TILE_WIDTH * 0.5

    /** 貼著排時與鄰居角度相差的最大值。 */
    private const val NEIGHBOUR_TURN_DEGREES: Double = 25.0

    /** 補空隙時連續放不下幾次就結束。 */
    private const val INFILL_PATIENCE: Int = 6_000

    /** 補空隙時與鄰居之間多留的最大空隙。 */
    private const val INFILL_SPACING: Double = MahjongTileDimensions.TILE_WIDTH * 0.05

    /** 補空隙時與鄰居角度相差的最大值。 */
    private const val INFILL_TURN_DEGREES: Double = 8.0

    /** 加上縫隙後牌的半對角線長；兩張牌中心相距超過兩倍時不可能重疊。 */
    private val PADDED_HALF_DIAGONAL: Double = hypot(
        MahjongTileDimensions.TILE_WIDTH / 2.0 + MahjongTileDimensions.TILE_SMALL_PADDING / 2.0,
        MahjongTileDimensions.TILE_HEIGHT / 2.0 + MahjongTileDimensions.TILE_SMALL_PADDING / 2.0,
    )

    /** 疊到上一層時每層的高度。 */
    private const val LAYER_HEIGHT: Double = MahjongTileDimensions.TILE_DEPTH + MahjongTileDimensions.TILE_SMALL_PADDING

    /** 一整圈的角度。 */
    private const val FULL_TURN_DEGREES: Double = 360.0
}

/**
 * 依全桌打牌順序為一張牌挑位置所需的資料。
 *
 * @property tileId 牌。
 * @property towardX 打牌者所在方向的單位向量 X。
 * @property towardZ 打牌者所在方向的單位向量 Z。
 * @property isTaken 是否已被鳴走。
 */
internal data class ScatterRequest(val tileId: Uuid, val towardX: Double, val towardZ: Double, val isTaken: Boolean)

/**
 * 散放牌河中一張牌的位置與角度，以桌子為準的座標。
 *
 * @property x 往南側座位右手邊為正。
 * @property y 往上疊的高度。
 * @property z 往南側座位為正。
 * @property angleDegrees 相對南側座位方向的角度，`0` 到 `360` 度。
 */
internal data class ScatteredDiscard(val x: Double, val y: Double, val z: Double, val angleDegrees: Float)
