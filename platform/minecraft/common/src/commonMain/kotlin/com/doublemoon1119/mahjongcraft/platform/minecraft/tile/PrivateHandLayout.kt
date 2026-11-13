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
 * 本人排列由呼叫端依時間軸提供，與共享呈現的牌張動作同時生效；這裡只負責把排列轉成每張牌的位置與過渡。
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

    /** 目前的牌排是否只由部分立牌推算；之後所有立牌排成等距連續的一列時直接改用兩端的中點。 */
    private var rowProvisional = false

    /** 最後確認牌排時的原生終點；未對齊的中間封包不能覆蓋這份基準。 */
    private var previousTargets = emptyMap<Uuid, TileDisplayTransform>()

    /** 已經顯示過的本人牌張，重新接管時不能當成首次進場瞬間定位。 */
    private val seen = mutableSetOf<Uuid>()

    /** 上一次的本人立牌順序（含空格），用來判斷換位時哪些牌會越過其他牌。 */
    private var lastStandingOrder = emptyList<Uuid?>()

    /** 上一次還沒理過、接在最右邊的牌。 */
    private var lastUnsortedIds = emptyList<Uuid>()

    /** 上一次在本人摸牌位上的牌；它加入立牌時以插入動作越過其他牌。 */
    private var lastDrawnId: Uuid? = null

    /** 依本人順序換位時會越過其他牌、下次換到新格位時要以弧線越過的牌。 */
    private val crossingIds = mutableSetOf<Uuid>()

    /** 只是重新排序（立牌組成不變）時，下次換到新格位要直接到位的牌。 */
    private val snappingIds = mutableSetOf<Uuid>()

    /**
     * 更新本人布局，不因某張牌動畫中或資料缺少而取消其他牌的排序。
     *
     * @param time 本幀世界時間，包含部分 tick。
     * @param standingOrder 本人完整立牌順序；null 是保留格位的空格，缺少原生資料的 ID 仍保留格位。
     * @param drawnId 獨立摸牌位的牌；沒有時為 null。
     * @param inputs 目前已追蹤的共享牌張資料，以真實牌張 ID 索引。
     * @param releaseHand 和牌或完整攤牌時，立即交回共享呈現，不另外補間私人牌位；倒牌動畫由共享呈現播放。
     * @param releaseTileIds 立即交回共享呈現的個別牌張，不使用缺少封包時的短暫保留機制。
     * @param unsortedIds 還沒理過、共享呈現接在立牌最右邊的牌，依共享呈現的順序排列；用來對照共享位置推算牌排。
     * @return 本幀由私人布局接管的最終絕對位置與姿態。
     */
    fun update(
        time: Double,
        standingOrder: List<Uuid?>,
        drawnId: Uuid?,
        inputs: Map<Uuid, TileDisplayInput>,
        releaseHand: Boolean,
        releaseTileIds: Set<Uuid> = emptySet(),
        unsortedIds: List<Uuid> = emptyList(),
    ): Map<Uuid, TileDisplayTransform> {
        if (releaseHand) {
            clear()
            return emptyMap()
        }
        val now = max(time, lastTime)
        lastTime = now
        val standingIds = standingOrder.filterNotNull()
        if (standingIds.size != standingIds.toSet().size || drawnId in standingIds) {
            clear()
            return emptyMap()
        }
        val privateIds = (standingIds + listOfNotNull(drawnId)).toSet()
        if (standingOrder != lastStandingOrder) {
            val previousIds = lastStandingOrder.filterNotNull()
            crossingIds.clear()
            crossingIds += crossingTiles(previousIds, standingIds, lastDrawnId)
            // 整副重新排序（例如切換自動理牌）不是牌的實際移動，直接到位。
            snappingIds.clear()
            val resorted = previousIds.isNotEmpty() &&
                standingIds != previousIds &&
                standingIds.toSet() == previousIds.toSet() &&
                drawnId == lastDrawnId &&
                unsortedIds == lastUnsortedIds
            if (resorted) {
                snappingIds += standingIds
                crossingIds.clear()
            }
            lastStandingOrder = standingOrder
        }
        lastUnsortedIds = unsortedIds
        lastDrawnId = drawnId
        val unsorted = unsortedIds.filter { it in standingIds }
        updateRow(
            publicOrder = PublicHandOrder.arrange(standingIds.filterNot { it in unsorted }) + unsorted,
            drawnId = drawnId,
            inputs = inputs,
        )
        val targets = buildMap {
            standingOrder.forEachIndexed { slot, id ->
                // 仍在牌牆或發牌尚未翻起的牌不能提前搬進手牌；空格與缺資料的牌仍保留完整格位索引。
                if (id != null && inputs[id]?.target?.poseDegrees == 0.0f) row?.placement(slot, standingOrder.size)?.let { put(id, it) }
            }
            drawnId?.let { id -> inputs[id]?.target?.takeIf { it.poseDegrees == 0.0f }?.let { put(id, it) } }
        }
        val frame = mutableMapOf<Uuid, TileDisplayTransform>()
        (entries.keys + privateIds).toList().forEach { id ->
            if (id in releaseTileIds) {
                entries.remove(id)
                return@forEach
            }
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
                val motionSpec = input.motion?.spec
                if (motionSpec != null && motionSpec.startPoseRotationDegrees != motionSpec.endPoseRotationDegrees && id !in seen) {
                    // 第一次接手時正在翻牌（例如開局發完牌翻起）：牌背還朝上，直接放到本人格位，翻牌在新位置原地播放。
                    entry.move(
                        from = shared.copy(position = target.position, yawDegrees = target.yawDegrees),
                        to = target,
                        now = now,
                        ticks = duration(input, now),
                        spec = motionSpec.copy(arcHeight = 0.0, liftCarryLower = false, leanDegrees = 0.0f),
                    )
                } else if (input.motion != null || id in seen) {
                    entry.move(
                        from = shared,
                        to = target,
                        now = now,
                        ticks = duration(input, now),
                        spec = input.motion?.spec,
                    )
                }
                entries[id] = entry
                seen += id
            } else {
                val current = entry.sample(now)
                if (target != null) {
                    entry.waitingSince = null
                    if (entry.isPrivate && entry.target != target && snappingIds.remove(id)) {
                        entry.snap(target)
                    } else if (!entry.isPrivate || entry.target != target) {
                        // 依本人順序會越過其他牌的牌從上方以弧線越過，其餘一起平移，不會互相穿過。
                        val crosses = entry.isPrivate && crossingIds.remove(id)
                        entry.move(
                            from = current,
                            to = target,
                            now = now,
                            ticks = if (crosses) MahjongTileTableLayout.ROW_ARC_DURATION_TICKS else REORDER_TICKS,
                            spec = if (crosses) ROW_ARC_SPEC else null,
                        )
                    }
                    entry.isPrivate = true
                } else {
                    val motionChanged = input.motion != null && input.motion != entry.input.motion
                    val endpointChanged = input.target != entry.input.target
                    if (entry.isPrivate && entry.waitingSince == null) entry.waitingSince = now
                    // 離開本人排列的牌等共享呈現讓它離開才交回；共享資料沒有跟上時只短暫保留。
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
                            spec = input.motion?.spec,
                        )
                        entry.isPrivate = false
                    } else if (!entry.isPrivate && (endpointChanged || motionChanged)) {
                        entry.move(
                            from = current,
                            to = input.target,
                            now = now,
                            ticks = duration(input, now),
                            spec = input.motion?.spec,
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
        lastStandingOrder = emptyList()
        lastUnsortedIds = emptyList()
        lastDrawnId = null
        crossingIds.clear()
        snappingIds.clear()
        row = null
        rowProvisional = false
        previousTargets = emptyMap()
        lastTime = Double.NEGATIVE_INFINITY
    }

    /**
     * 從已同步的立牌終點核對牌排中心；不以移動中的距離猜牌寬。
     *
     * 牌排中心改變時，至少要有一張牌的原生位置跟上次確認時不同，或整手都是沒有上次位置的新牌，才採用新中心。
     *
     * @param publicOrder 完整公開立牌順序。
     * @param drawnId 獨立摸牌位的牌，用於排除共享位置已合併、本人排列還沒更新的中間狀態。
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
        // 所有立牌都已立起時，牌排是等距連續的一列，中心就是兩端的中點，與每張牌排在第幾格無關；
        // 本機對還沒理過的牌的認定與共享擺放稍有不同時，逐張推算的中心會對不上。
        val extent = extentRow(publicOrder, inputs)
        val candidate = extent ?: run {
            val first = candidates.firstOrNull() ?: return
            if (candidates.any { !first.matches(it) }) return
            first
        }
        if (row?.matches(candidate) == false) {
            // 換格數的本人排列可能比共享位置先到；不能因為等了幾幀就把舊座標當成新中心。
            val moved = publicOrder.any { id -> previousTargets[id]?.let { it != inputs[id]?.target } == true }
            // 整手換成全新的牌（例如載入 debug 情境）時沒有舊座標可比，全部立起且一致的位置就是新牌排。
            val replaced = publicOrder.none { id -> id in previousTargets }
            // 牌排多了一張牌、全部排成等距連續的一列時，其餘牌的位置可能在張數改變前就已經換好、記在上次的位置裡；
            // 新加入的牌已有位置，兩端就是新的牌排。
            val joined = extent != null && publicOrder.any { id -> id !in previousTargets }
            // 只由部分立牌推算的牌排可能沿用已經離開的牌的格位；所有立牌排成一列後就以兩端為準。
            val confirmed = extent != null && rowProvisional
            // 還沒立起的牌（仍在牌牆上）不在牌排上，不影響核對；缺資料的牌則要等資料到齊。
            val notStanding = publicOrder.count { id -> inputs[id]?.target?.let { it.poseDegrees != 0.0f } == true }
            if (candidates.size + notStanding != publicOrder.size || !(moved || replaced || joined || confirmed)) return
            if (drawnId != null) {
                val drawn = inputs[drawnId]?.target ?: return
                val edge = candidate.placement(publicOrder.lastIndex, publicOrder.size)
                val angle = candidate.yaw * PI / 180.0
                val gap = (drawn.position.x - edge.position.x) * cos(angle) + (drawn.position.z - edge.position.z) * sin(angle)
                // 原生位置先合併摸牌時，本人排列仍稱它是摸牌；此時不能用舊張數推算中心。還沒立起的摸牌（仍在牌牆上或正要飛來）
                // 不可能已經併入立牌，不擋牌排更新，否則手牌要等它落地才讓位。
                if (drawn.poseDegrees == 0.0f && gap <= TILE_STEP + GEOMETRY_EPSILON) return
            }
        }
        row = candidate
        rowProvisional = extent == null && candidates.size < publicOrder.size
        previousTargets = publicOrder.mapNotNull { id -> inputs[id]?.target?.let { id to it } }.toMap()
    }

    /**
     * 所有立牌都已立起、排成等距連續的一列時，以兩端的中點作為牌排中心。
     *
     * @param publicOrder 完整公開立牌順序。
     * @param inputs 本幀原生資料。
     * @return 牌排；有牌還沒立起、缺少資料，或位置不是等距連續的一列時為 null。
     */
    private fun extentRow(
        publicOrder: List<Uuid>,
        inputs: Map<Uuid, TileDisplayInput>,
    ): Row? {
        val targets = publicOrder.map { id -> inputs[id]?.target?.takeIf { it.poseDegrees == 0.0f } ?: return null }
        val first = targets.firstOrNull() ?: return null
        val angle = first.yawDegrees * PI / 180.0
        fun along(target: TileDisplayTransform) = target.position.x * cos(angle) + target.position.z * sin(angle)
        fun across(target: TileDisplayTransform) = -target.position.x * sin(angle) + target.position.z * cos(angle)
        val acrossLine = across(first)
        val sameLine = targets.all { target ->
            abs(across(target) - acrossLine) < GEOMETRY_EPSILON &&
                abs(target.position.y - first.position.y) < GEOMETRY_EPSILON &&
                abs(((target.yawDegrees - first.yawDegrees + 540.0f) % 360.0f) - 180.0f) < GEOMETRY_EPSILON
        }
        if (!sameLine) return null
        val alongs = targets.map(::along)
        val min = alongs.min()
        val max = alongs.max()
        if (abs(max - min - (targets.size - 1) * TILE_STEP) > GEOMETRY_EPSILON) return null
        val middle = (min + max) / 2.0
        return Row(
            center = DiceAnimationVector(
                x = middle * cos(angle) - acrossLine * sin(angle),
                y = first.position.y,
                z = middle * sin(angle) + acrossLine * cos(angle),
            ),
            yaw = first.yawDegrees,
        )
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
         * 位置沿牌排方向離中心的距離，往摸牌位那一側為正。
         *
         * @param transform 要量的位置。
         * @return 沿牌排方向的距離。
         */
        fun alongOf(transform: TileDisplayTransform): Double {
            val angle = yaw * PI / 180.0
            return (transform.position.x - center.x) * cos(angle) + (transform.position.z - center.z) * sin(angle)
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
     * 依本人順序換位時會越過其他牌的牌：兩次順序都有的牌中，能維持原本前後順序的最多一組之外的牌，以及從摸牌位插入立牌的牌。
     * 第一次出現在立牌的牌（例如開局發完牌）不算插入。
     *
     * @param previous 上一次的本人立牌順序。
     * @param current 這一次的本人立牌順序。
     * @param previousDrawnId 上一次在摸牌位上的牌；沒有時為 null。
     * @return 會越過其他牌的牌。
     */
    private fun crossingTiles(previous: List<Uuid>, current: List<Uuid>, previousDrawnId: Uuid?): Set<Uuid> {
        val previousIndex = previous.withIndex().associate { (index, id) -> id to index }
        val currentIndex = current.withIndex().associate { (index, id) -> id to index }
        val stayed = current.filter { it in previousIndex }.sortedBy { previousIndex.getValue(it) }
        val kept = TableStepMotionPlanner.longestIncreasingSubsequence(stayed.map { currentIndex.getValue(it) }).mapTo(mutableSetOf()) { stayed[it] }
        val inserted = setOfNotNull(previousDrawnId?.takeIf { it in currentIndex })
        return stayed.toSet() - kept + inserted
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
         * @param spec 沿用的路線、弧線高度、傾斜與旋轉曲線；為 null 時沒有弧線，直接平移。
         */
        fun move(
            from: TileDisplayTransform,
            to: TileDisplayTransform,
            now: Double,
            ticks: Int,
            spec: TileMotionAnimationSpec?,
        ) {
            start = from
            target = to
            startedAt = now
            this.spec = TileMotionAnimationSpec(
                durationTicks = ticks,
                arcHeight = spec?.arcHeight ?: 0.0,
                startPoseRotationDegrees = from.poseDegrees,
                endPoseRotationDegrees = to.poseDegrees,
                easeRotation = spec?.easeRotation ?: false,
                liftCarryLower = spec?.liftCarryLower ?: false,
                leanDegrees = spec?.leanDegrees ?: 0.0f,
            )
        }

        /**
         * 不播放過渡，直接停在新終點。
         *
         * @param to 新的顯示終點。
         */
        fun snap(to: TileDisplayTransform) {
            start = to
            target = to
            spec = null
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
                lean = motion.lean,
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
    internal companion object {
        /** 一般理牌與補位時間，沿用牌張預設動畫長度。 */
        const val REORDER_TICKS = TileMotionAnimationSpec.DEFAULT_DURATION_TICKS

        /** 本人立牌越過其他牌時的路線與傾斜，與共享呈現的同排弧線相同；時長與起訖姿態由每次移動決定。 */
        val ROW_ARC_SPEC = TileMotionAnimationSpec(
            durationTicks = MahjongTileTableLayout.ROW_ARC_DURATION_TICKS,
            arcHeight = MahjongTileTableLayout.ROW_ARC_HEIGHT,
            startPoseRotationDegrees = 0.0f,
            endPoseRotationDegrees = 0.0f,
            liftCarryLower = true,
            leanDegrees = MahjongTileTableLayout.ROW_ARC_LEAN_DEGREES,
        )

        /** 等待本人排列與原生資料對齊的最長時間，避免永久停在舊位置。 */
        const val HANDOFF_GRACE_TICKS = 3.0

        /** 與共享牌區相同的固定牌距，不由相鄰實體距離反推。 */
        const val TILE_STEP = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING

        /** 封包座標量化誤差容許值，小於半張牌距。 */
        const val GEOMETRY_EPSILON = 0.004
    }
}
