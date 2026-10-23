package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.PublicHandOrder
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.uuid.Uuid

/**
 * 保留本人每張牌實際顯示的位置，將私人理牌與共享蓋牌的換位分開。
 *
 * 每次更新產生一份新的不可變結果；原生讀取、渲染與命中由平台接線處理。
 * 換世界、換桌局或換回合時，呼叫端必須使用 [clear] 清除上一輪暫存。
 */
class PrivateHandLayout {
    /** 只有已接管的本人牌張才有項目，離手交接結束後立即移除。 */
    private val entries = mutableMapOf<Uuid, Entry>()

    /** 防止暫停或部分 tick 回退讓已播放的動畫倒退。 */
    private var lastTime = Double.NEGATIVE_INFINITY

    /** 已確認的固定牌排幾何，不跟隨不同步抵達的單張換位封包抖動。 */
    private var row: Row? = null

    /** 最後確認牌排時的原生終點；未對齊的中間封包不能覆蓋這份基準。 */
    private var previousTargets = emptyMap<Uuid, TileDisplayTransform>()

    /** 已經顯示過的本人牌張，重新接管時不能當成首次進場瞬間定位。 */
    private val seen = mutableSetOf<Uuid>()

    /**
     * 更新本人布局，不因某張牌動畫中或資料缺少而取消其他牌的排序。
     *
     * @param time 本幀世界時間，包含部分 tick。
     * @param standingOrder 本人完整立牌順序；缺少原生資料的 ID 仍須保留。
     * @param drawnId 獨立摸牌位的牌；沒有時為 null。
     * @param inputs 目前已追蹤的共享牌張資料，以真實牌張 ID 索引。
     * @param releaseHand 和牌或完整攤牌時，立即交回共享呈現，不另外補間私人牌位；倒牌動畫由共享呈現播放。
     * @return 本幀由私人布局接管的最終絕對位置與姿態。
     */
    fun update(
        time: Double,
        standingOrder: List<Uuid>,
        drawnId: Uuid?,
        inputs: Map<Uuid, TileDisplayInput>,
        releaseHand: Boolean,
    ): Map<Uuid, TileDisplayTransform> {
        if (releaseHand) {
            clear()
            return emptyMap()
        }
        val now = max(time, lastTime)
        lastTime = now
        if (standingOrder.size != standingOrder.toSet().size || drawnId in standingOrder) {
            clear()
            return emptyMap()
        }
        val privateIds = (standingOrder + listOfNotNull(drawnId)).toSet()
        val publicOrder = PublicHandOrder.arrange(standingOrder)
        updateRow(
            publicOrder = publicOrder,
            drawnId = drawnId,
            inputs = inputs,
        )
        val targets = buildMap {
            standingOrder.forEachIndexed { slot, id ->
                // 仍在牌牆或發牌尚未翻起的牌不能提前搬進手牌。缺資料仍保留完整格位索引。
                if (inputs[id]?.target?.poseDegrees == 0.0f) row?.placement(slot, standingOrder.size)?.let { put(id, it) }
            }
            drawnId?.let { id -> inputs[id]?.target?.takeIf { it.poseDegrees == 0.0f }?.let { put(id, it) } }
        }
        val frame = mutableMapOf<Uuid, TileDisplayTransform>()
        (entries.keys + privateIds).toList().forEach { id ->
            val input = inputs[id]
            var entry = entries[id]
            if (input == null || !input.visible) {
                if (entry != null && now - entry.lastAvailable <= HANDOFF_GRACE_TICKS) {
                    frame[id] = entry.sample(now)
                } else {
                    entries.remove(id)
                }
                return@forEach
            }
            val target = targets[id].takeIf { id in privateIds }
            if (entry == null) {
                if (target == null) return@forEach
                val shared = input.sample(now)
                entry = Entry(
                    target = target,
                    input = input,
                    lastAvailable = now,
                )
                if (input.motion != null || id in seen) {
                    entry.move(
                        from = shared,
                        to = target,
                        now = now,
                        ticks = duration(input, now),
                        arcHeight = input.motion?.spec?.arcHeight ?: 0.0,
                        easeRotation = input.motion?.spec?.easeRotation ?: false,
                    )
                }
                entries[id] = entry
                seen += id
            } else {
                val current = entry.sample(now)
                if (target != null) {
                    entry.waitingSince = null
                    if (!entry.isPrivate || entry.target != target) {
                        entry.move(
                            from = current,
                            to = target,
                            now = now,
                            ticks = REORDER_TICKS,
                            arcHeight = 0.0,
                        )
                    }
                    entry.isPrivate = true
                } else {
                    val motionChanged = input.motion != null && input.motion != entry.input.motion
                    val endpointChanged = input.target != entry.input.target
                    if (entry.isPrivate && entry.waitingSince == null) entry.waitingSince = now
                    val ready = motionChanged ||
                        endpointChanged ||
                        input.target.poseDegrees != 0.0f ||
                        now - (entry.waitingSince ?: now) >= HANDOFF_GRACE_TICKS
                    if (entry.isPrivate && ready) {
                        entry.move(
                            from = current,
                            to = input.target,
                            now = now,
                            ticks = duration(input, now),
                            arcHeight = input.motion?.spec?.arcHeight ?: 0.0,
                            easeRotation = input.motion?.spec?.easeRotation ?: false,
                        )
                        entry.isPrivate = false
                    } else if (!entry.isPrivate && (endpointChanged || motionChanged)) {
                        entry.move(
                            from = current,
                            to = input.target,
                            now = now,
                            ticks = duration(input, now),
                            arcHeight = input.motion?.spec?.arcHeight ?: 0.0,
                            easeRotation = input.motion?.spec?.easeRotation ?: false,
                        )
                    }
                    if (!entry.isPrivate && entry.finished(now) && input.sample(now) == entry.target) {
                        entries.remove(id)
                        return@forEach
                    }
                }
            }
            entry.input = input
            entry.lastAvailable = now
            frame[id] = entry.sample(now)
        }
        return frame.toMap()
    }

    /** 清除回合與連線專用的所有位置、過渡及時間暫存。 */
    fun clear() {
        entries.clear()
        seen.clear()
        row = null
        previousTargets = emptyMap()
        lastTime = Double.NEGATIVE_INFINITY
    }

    /**
     * 從已同步的立牌終點核對牌排中心；不以移動中的距離猜牌寬。
     *
     * 牌排中心改變時，至少要有一張牌的原生位置跟上次確認時不同，或整手都是沒有上次位置的新牌，才採用新中心。
     *
     * @param publicOrder 完整公開立牌順序。
     * @param drawnId 獨立摸牌位的牌，用於排除已合併但快照尚未更新的中間狀態。
     * @param inputs 本幀原生資料。
     */
    private fun updateRow(
        publicOrder: List<Uuid>,
        drawnId: Uuid?,
        inputs: Map<Uuid, TileDisplayInput>,
    ) {
        val candidates = publicOrder.mapIndexedNotNull { index, id ->
            inputs[id]?.target?.takeIf { it.poseDegrees == 0.0f }?.let { target ->
                val angle = target.yawDegrees * PI / 180.0
                val along = (index - (publicOrder.size - 1) / 2.0) * TILE_STEP
                Row(
                    center = DiceAnimationVector(
                        x = target.position.x - cos(angle) * along,
                        y = target.position.y,
                        z = target.position.z - sin(angle) * along,
                    ),
                    yaw = target.yawDegrees,
                )
            }
        }
        val candidate = candidates.firstOrNull() ?: return
        if (candidates.any { !candidate.matches(it) }) return
        if (row?.matches(candidate) == false) {
            // 換格數的快照可能比位置先到；不能因為等了幾幀就把舊座標當成新中心。
            val moved = publicOrder.any { id -> previousTargets[id]?.let { it != inputs[id]?.target } == true }
            // 整手換成全新的牌（例如載入 debug 情境）時沒有舊座標可比，全部立起且一致的位置就是新牌排。
            val replaced = publicOrder.none { id -> id in previousTargets }
            if (candidates.size != publicOrder.size || !(moved || replaced)) return
            if (drawnId != null) {
                val drawn = inputs[drawnId]?.target ?: return
                val edge = candidate.placement(publicOrder.lastIndex, publicOrder.size)
                val angle = candidate.yaw * PI / 180.0
                val gap = (drawn.position.x - edge.position.x) * cos(angle) + (drawn.position.z - edge.position.z) * sin(angle)
                // 原生位置先合併摸牌時，快照仍稱它是摸牌；此時不能用舊張數推算中心。還沒立起的摸牌（仍在牌牆上或正要飛來）
                // 不可能已經併入立牌，不擋牌排更新，否則手牌要等它落地才讓位。
                if (drawn.poseDegrees == 0.0f && gap <= TILE_STEP + GEOMETRY_EPSILON) return
            }
        }
        row = candidate
        previousTargets = publicOrder.mapNotNull { id -> inputs[id]?.target?.let { id to it } }.toMap()
    }

    /**
     * 固定尺寸牌排的中心與方向；只有所有可用終點都一致時才更新。
     *
     * @property center 包含角落讓位偏移的牌排中心。
     * @property yaw 立牌的共享朝向。
     */
    private data class Row(val center: DiceAnimationVector, val yaw: Float) {
        /**
         * 使用固定牌距計算私人格位，資料缺少的格位不會被擠掉。
         *
         * @param slot 本人的完整順序索引。
         * @param size 完整立牌張數。
         * @return 該格位的直立牌終點。
         */
        fun placement(slot: Int, size: Int): TileDisplayTransform {
            val along = (slot - (size - 1) / 2.0) * TILE_STEP
            val angle = yaw * PI / 180.0
            return TileDisplayTransform(
                position = DiceAnimationVector(
                    x = center.x + cos(angle) * along,
                    y = center.y,
                    z = center.z + sin(angle) * along,
                ),
                yawDegrees = yaw,
                poseDegrees = 0.0f,
            )
        }

        /**
         * 允許封包座標量化的微小誤差，不接受仍混用新舊格位的牌排。
         *
         * @param other 另一張立牌推算的幾何。
         * @return 是否代表同一個中心與朝向。
         */
        fun matches(other: Row): Boolean = abs(center.x - other.center.x) < GEOMETRY_EPSILON &&
            abs(center.y - other.center.y) < GEOMETRY_EPSILON &&
            abs(center.z - other.center.z) < GEOMETRY_EPSILON &&
            abs(((yaw - other.yaw + 540.0f) % 360.0f) - 180.0f) < GEOMETRY_EPSILON
    }

    /**
     * 已開始的世界動畫只銜接剩餘時間；沒有可用動畫時使用短補位時間。
     *
     * @param input 該牌的共享動畫資料。
     * @param now 當下顯示時間。
     * @return 大於零的銜接時長。
     */
    private fun duration(input: TileDisplayInput, now: Double): Int = input.motion?.let {
        ceil(it.startedAt + it.spec.durationTicks - now).toInt().coerceIn(1, it.spec.durationTicks)
    } ?: REORDER_TICKS

    /**
     * 單張牌目前的布局與過渡；起點永遠是中斷當刻已顯示的絕對位置。
     *
     * @property target 這段過渡的終點。
     * @property input 上一幀原生資料，用於辨識動畫與終點變更。
     * @property lastAvailable 最近有完整原生資料的時間。
     */
    private class Entry(
        var target: TileDisplayTransform,
        var input: TileDisplayInput,
        var lastAvailable: Double,
    ) {
        /** 是否仍使用本人格位，包含等待離手資料銜接的短暫期間。 */
        var isPrivate = true

        /** 開始等待離手／缺格資料的時間。 */
        var waitingSince: Double? = null

        /** 本段不可變起點，不能每幀用已衰減位移重建。 */
        private var start = target

        /** 本段開始時間。 */
        private var startedAt = Double.NEGATIVE_INFINITY

        /** 本段動畫參數；靜止時為 null。 */
        private var spec: TileMotionAnimationSpec? = null

        /**
         * 中斷舊過渡並銜接新終點。
         *
         * @param from 中斷當刻的位置與姿態。
         * @param to 新的顯示終點。
         * @param now 本次過渡開始時間。
         * @param ticks 過渡時長。
         * @param arcHeight 飛行弧線高度；一般補位為零。
         * @param easeRotation 是否沿用共享動畫的旋轉緩出效果。
         */
        fun move(
            from: TileDisplayTransform,
            to: TileDisplayTransform,
            now: Double,
            ticks: Int,
            arcHeight: Double,
            easeRotation: Boolean = false,
        ) {
            start = from
            target = to
            startedAt = now
            spec = TileMotionAnimationSpec(
                durationTicks = ticks,
                arcHeight = arcHeight,
                startPoseRotationDegrees = from.poseDegrees,
                endPoseRotationDegrees = to.poseDegrees,
                easeRotation = easeRotation,
            )
        }

        /**
         * 以同一套運動曲線產生最終絕對 transform，不再交由 renderer 重複疊加。
         *
         * @param now 本幀世界時間。
         * @return 本人當下看見的位置與姿態。
         */
        fun sample(now: Double): TileDisplayTransform {
            val activeSpec = spec ?: return target
            val offset = DiceAnimationVector(
                start.position.x - target.position.x,
                start.position.y - target.position.y,
                start.position.z - target.position.z,
            )
            val yawDifference = ((start.yawDegrees - target.yawDegrees + 540.0f) % 360.0f) - 180.0f
            val motion = TileMotionAnimation(activeSpec).frame(now - startedAt, offset, yawDifference)
            return TileDisplayTransform(
                position = DiceAnimationVector(
                    target.position.x + motion.offset.x,
                    target.position.y + motion.offset.y,
                    target.position.z + motion.offset.z,
                ),
                yawDegrees = target.yawDegrees + motion.yawOffsetDegrees,
                poseDegrees = motion.poseRotationDegrees,
            )
        }

        /**
         * 判斷這張牌是否已到達本段終點。
         *
         * @param now 本幀世界時間。
         * @return 未在過渡或已播放完成時為 true。
         */
        fun finished(now: Double): Boolean = spec?.let { now >= startedAt + it.durationTicks } ?: true
    }

    /** 私人顯示使用的短過渡時間，不影響權威操作計時。 */
    private companion object {
        /** 一般理牌與補位時間，沿用牌張預設動畫長度。 */
        const val REORDER_TICKS = TileMotionAnimationSpec.DEFAULT_DURATION_TICKS

        /** 等待快照與原生資料對齊的最長時間，避免永久停在舊位置。 */
        const val HANDOFF_GRACE_TICKS = 3.0

        /** 與共享牌區相同的固定牌距，不由相鄰實體距離反推。 */
        const val TILE_STEP = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING

        /** 封包座標量化誤差容許值，小於半張牌距。 */
        const val GEOMETRY_EPSILON = 0.004
    }
}
