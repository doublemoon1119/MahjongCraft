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

    /** 上一次實際排列的本人立牌順序（還沒理過的牌接在最右邊），用來判斷換位時哪些牌會越過其他牌。 */
    private var lastStandingOrder = emptyList<Uuid>()

    /** 上一次快照的本人立牌順序；只有快照順序改變時才可能是單純重新排序。 */
    private var lastSnapshotOrder = emptyList<Uuid>()

    /** 上一次還沒理過、接在最右邊的牌。 */
    private var lastUnsortedIds = emptySet<Uuid>()

    /** 上一次在本人摸牌位上的牌；它加入立牌時以插入動作越過其他牌。 */
    private var lastDrawnId: Uuid? = null

    /** 依本人順序換位時會越過其他牌、下次換到新格位時要以弧線越過的牌。 */
    private val crossingIds = mutableSetOf<Uuid>()

    /** 只是重新排序（立牌組成不變）時，下次換到新格位要直接到位的牌。 */
    private val snappingIds = mutableSetOf<Uuid>()

    /** 本人立牌順序改變、還在等共享呈現開始讓位的時間；不在等待時為 null。 */
    private var orderChangedAt: Double? = null

    /** 上一幀的共享終點，用來判斷順序改變之後共享的牌是否已經移到新位置。 */
    private var lastFrameTargets = emptyMap<Uuid, TileDisplayTransform>()

    /** 順序改變前一幀的共享終點；共享的牌離開這些位置，代表共享呈現已經開始讓位。 */
    private var targetsAtOrderChange = emptyMap<Uuid, TileDisplayTransform>()

    /** 順序改變前後都在本人手牌的牌；其中任一張在共享呈現中開始移動，代表手牌開始讓位。 */
    private var stayedIds = emptySet<Uuid>()

    /** 最近一次順序改變時離開本人手牌的牌；都已在共享呈現中落地而手牌沒有讓位時，本人換位也開始。 */
    private var leftIds = emptySet<Uuid>()

    /** 已離開本人手牌、還沒在共享呈現中開始離開的牌；在那之前留在本人格位。 */
    private val leavingIds = mutableSetOf<Uuid>()

    /**
     * 更新本人布局，不因某張牌動畫中或資料缺少而取消其他牌的排序。
     *
     * @param time 本幀世界時間，包含部分 tick。
     * @param standingOrder 本人完整立牌順序；缺少原生資料的 ID 仍須保留。
     * @param drawnId 獨立摸牌位的牌；沒有時為 null。
     * @param inputs 目前已追蹤的共享牌張資料，以真實牌張 ID 索引。
     * @param releaseHand 和牌或完整攤牌時，立即交回共享呈現，不另外補間私人牌位；倒牌動畫由共享呈現播放。
     * @param releaseTileIds 立即交回共享呈現的個別牌張，不使用缺少封包時的短暫保留機制。
     * @return 本幀由私人布局接管的最終絕對位置與姿態。
     */
    fun update(
        time: Double,
        standingOrder: List<Uuid>,
        drawnId: Uuid?,
        inputs: Map<Uuid, TileDisplayInput>,
        releaseHand: Boolean,
        releaseTileIds: Set<Uuid> = emptySet(),
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
        // 快照可能比共享呈現早到：還在牌牆上、從沒進過手牌的牌還不佔格位，等它進入手牌才排進來。缺資料的牌仍保留格位。
        val present = standingOrder.filter { id -> inputs[id]?.let { it.target.poseDegrees == 0.0f || id in seen } ?: true }
        // 共享呈現中直接補進立牌、還沒理過的牌，依共享位置接在最右邊；理牌時才插入本人順序。
        val unsorted = present.filter { id -> inputs[id]?.let { it.unsorted && it.target.poseDegrees == 0.0f } == true }
            .sortedBy { id -> row?.alongOf(inputs.getValue(id).target) ?: 0.0 }
        val unsortedIds = unsorted.toSet()
        val settled = present.filterNot { it in unsortedIds }
        val effectiveOrder = settled + unsorted
        if (effectiveOrder != lastStandingOrder) {
            crossingIds.clear()
            crossingIds += crossingTiles(lastStandingOrder, effectiveOrder, lastDrawnId?.takeUnless { it in unsortedIds })
            // 整副重新排序（例如切換自動理牌）不是牌的實際移動，直接到位。
            snappingIds.clear()
            val resorted = standingOrder != lastSnapshotOrder && unsortedIds == lastUnsortedIds
            if (resorted && lastStandingOrder.isNotEmpty() && effectiveOrder.toSet() == lastStandingOrder.toSet() && drawnId == lastDrawnId) {
                snappingIds += effectiveOrder
                crossingIds.clear()
            }
            // 有牌離開手牌（打出或鳴走）時，共享呈現先讓那張牌落地才讓位；只是重新排序時沒有要等的動作。
            val left = lastStandingOrder.filterNot { it in privateIds }
            if (left.isNotEmpty()) {
                orderChangedAt = now
                targetsAtOrderChange = lastFrameTargets
                stayedIds = (lastStandingOrder + listOfNotNull(lastDrawnId)).filterTo(mutableSetOf()) { it in privateIds }
                leftIds = left.toSet()
                leavingIds += left
            }
            lastStandingOrder = effectiveOrder
        }
        lastSnapshotOrder = standingOrder
        lastUnsortedIds = unsortedIds
        // 快照會比共享呈現早到：共享呈現依序播放，可能還在播前面的步驟。共享呈現在同一步中先讓離開的牌落地，再讓位與插入；
        // 本人換位等到原本就在手牌的牌開始移動才跟著開始，或離開的牌都已落地而手牌不需要讓位時開始，時機與其他人看到的一致。
        orderChangedAt?.let { changedAt ->
            val stayedMoved = stayedIds.any { id -> inputs[id]?.let { input -> input.movedSince(changedAt, targetsAtOrderChange[id]) } == true }
            val leftLanded = leftIds.all { id -> inputs[id]?.let { input -> input.landedSince(changedAt, targetsAtOrderChange[id], now) } ?: true }
            if (stayedMoved || leftLanded || now - changedAt >= ORDER_SYNC_TIMEOUT_TICKS) orderChangedAt = null
        }
        val waitingForShared = orderChangedAt != null
        lastDrawnId = drawnId
        lastFrameTargets = inputs.mapValues { (_, input) -> input.target }
        val publicOrder = PublicHandOrder.arrange(settled) + unsorted
        updateRow(
            publicOrder = publicOrder,
            drawnId = drawnId,
            inputs = inputs,
        )
        val targets = buildMap {
            effectiveOrder.forEachIndexed { slot, id ->
                // 仍在牌牆或發牌尚未翻起的牌不能提前搬進手牌。缺資料仍保留完整格位索引。
                if (inputs[id]?.target?.poseDegrees == 0.0f) row?.placement(slot, effectiveOrder.size)?.let { put(id, it) }
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
                    if (entry.isPrivate && entry.target != target && waitingForShared) {
                        // 還在等共享呈現開始讓位，先停在原本的位置。
                    } else if (entry.isPrivate && entry.target != target && snappingIds.remove(id)) {
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
                    // 已離開手牌的牌等共享呈現真的讓它離開才交回；其他缺格只短暫保留，等資料對齊。
                    val graceTicks = if (id in leavingIds) ORDER_SYNC_TIMEOUT_TICKS else HANDOFF_GRACE_TICKS
                    val ready = motionChanged ||
                        endpointChanged ||
                        input.target.poseDegrees != 0.0f ||
                        now - (entry.waitingSince ?: now) >= graceTicks
                    if (entry.isPrivate && ready) {
                        leavingIds -= id
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
        lastSnapshotOrder = emptyList()
        lastUnsortedIds = emptySet()
        lastDrawnId = null
        crossingIds.clear()
        snappingIds.clear()
        orderChangedAt = null
        lastFrameTargets = emptyMap()
        targetsAtOrderChange = emptyMap()
        stayedIds = emptySet()
        leftIds = emptySet()
        leavingIds.clear()
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
     * 這張牌在共享呈現中是否在 [since] 之後開始移動：有新的動畫，或終點已離開 [before]。
     *
     * @param since 開始觀察的時間。
     * @param before 開始觀察前一幀的共享終點；沒有時只看動畫。
     * @return 已開始移動時為 true。
     */
    private fun TileDisplayInput.movedSince(since: Double, before: TileDisplayTransform?): Boolean = motion?.let { it.startedAt >= since - MOTION_START_TOLERANCE_TICKS } == true || (before != null && before != target)

    /**
     * 這張牌在共享呈現中是否在 [since] 之後移動並已落地。
     *
     * @param since 開始觀察的時間。
     * @param before 開始觀察前一幀的共享終點；沒有時只看動畫。
     * @param now 本幀世界時間。
     * @return 已移動且動畫播完時為 true。
     */
    private fun TileDisplayInput.landedSince(since: Double, before: TileDisplayTransform?, now: Double): Boolean = movedSince(since, before) && motion?.let { now >= it.startedAt + it.spec.durationTicks } != false

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

        /**
         * 立牌順序改變後最多等共享呈現開始讓位的時間，避免共享的牌沒有移動時永久停在舊位置。
         *
         * 共享呈現依序播放，前面還有開局發牌或逐輪補花等步驟時，輪到這次換位可能要等好幾秒；這個值只是安全上限。
         */
        const val ORDER_SYNC_TIMEOUT_TICKS = 600.0

        /** 判斷共享動畫是不是這次換位之後才開始時，容許的時間誤差。 */
        const val MOTION_START_TOLERANCE_TICKS = 1.0

        /** 等待快照與原生資料對齊的最長時間，避免永久停在舊位置。 */
        const val HANDOFF_GRACE_TICKS = 3.0

        /** 與共享牌區相同的固定牌距，不由相鄰實體距離反推。 */
        const val TILE_STEP = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING

        /** 封包座標量化誤差容許值，小於半張牌距。 */
        const val GEOMETRY_EPSILON = 0.004
    }
}
