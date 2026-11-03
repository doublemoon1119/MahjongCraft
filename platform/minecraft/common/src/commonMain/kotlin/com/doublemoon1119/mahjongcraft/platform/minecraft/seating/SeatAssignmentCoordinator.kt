package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatIdentity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatTarget
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/**
 * 單次開局共用的固定席位協調器；不搜尋替代位置，不以站立覆蓋已占用座位。
 * 無法確認提供者的占用狀態時停止該乘坐者的自動處理，避免把未知狀態當作空位；其他乘坐者仍可繼續。
 *
 * @param W 原生世界型別。
 * @param R 原生乘坐者型別。
 * @param S 原位置快照型別。
 * @property providers 已依優先權排序的來源。
 * @property environment 平台安全操作。
 * @property failurePolicy 判斷提供者丟出的失敗能否隔離；無法隔離的失敗原樣往外傳。
 * @property report 提供者失敗的診斷；傳入提供者 ID、乘坐者與例外。
 */
class SeatAssignmentCoordinator<W, R, S>(
    private val providers: List<RegisteredSeatProvider<W, R>>,
    private val environment: SeatEnvironment<W, R, S>,
    private val failurePolicy: ExtensionFailurePolicy,
    private val report: (String, R, Throwable) -> Unit,
) {
    /** 已由本次開局成功認領的座位，跨不同提供者去重。 */
    private val claimed = mutableSetOf<SeatIdentity>()

    /**
     * 解析並入座；本元件需由平台在主執行緒依席位順序呼叫。
     *
     * @param world 目標世界。
     * @param rider 乘坐者。
     * @param request 固定席位請求。
     * @return 可供平台提示的結果。
     */
    fun assign(
        world: W,
        rider: R,
        request: SeatRequest,
    ): SeatAssignmentResult {
        val original = environment.snapshot(rider)
        var providerId = "unresolved"
        try {
            val relevant = inspectTargets(world, rider, request, original) { providerId = it }
            if (relevant.any { (_, target, state) -> state == SeatState.OCCUPIED || target.identity in claimed }) {
                return SeatAssignmentResult.OCCUPIED
            }
            val seated = relevant.firstOrNull { (_, _, state) -> state == SeatState.SEATED }
            if (seated != null) {
                providerId = seated.first
                if (!environment.hasVehicle(rider) || !environment.positioned(world, rider, seated.second.geometry(world, rider))) error("Seat provider reported false seated state")
                claimed += seated.second.identity
                return SeatAssignmentResult.SEATED
            }
            if (environment.hasVehicle(rider)) return SeatAssignmentResult.RIDING
            if (relevant.any { it.third == SeatState.FAILED }) return SeatAssignmentResult.FAILED
            val candidate = relevant.firstOrNull { it.third == SeatState.AVAILABLE }
            if (candidate != null) {
                providerId = candidate.first
                val target = candidate.second
                val state = target.inspect(world, rider)
                if (!environment.unchanged(rider, original)) error("Seat provider changed rider during revalidation")
                if (state == SeatState.OCCUPIED) return SeatAssignmentResult.OCCUPIED
                if (state == SeatState.AVAILABLE && matches(target.geometry(world, rider), request)) {
                    if (!environment.unchanged(rider, original)) error("Seat provider changed rider before seating")
                    val result = target.trySit(world, rider)
                    if (result == SeatState.SEATED) {
                        if (target.inspect(world, rider) != SeatState.SEATED || !environment.hasVehicle(rider) || !environment.positioned(world, rider, target.geometry(world, rider))) {
                            error("Seat provider reported success without completing seating")
                        }
                        claimed += target.identity
                        return SeatAssignmentResult.SEATED
                    }
                    if (!environment.unchanged(rider, original)) error("Seat provider left changes after failed seating")
                    if (result == SeatState.OCCUPIED) return SeatAssignmentResult.OCCUPIED
                    if (result == SeatState.FAILED || result == SeatState.AVAILABLE) return SeatAssignmentResult.FAILED
                }
            }
            if (!environment.unchanged(rider, original)) error("Seat provider changed rider during validation")
            return if (environment.stand(world, rider, request)) SeatAssignmentResult.STANDING else SeatAssignmentResult.BLOCKED
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            if (!failurePolicy.isIsolatable(cause)) throw cause
            report(providerId, rider, cause)
            environment.recover(rider, original)
            return SeatAssignmentResult.FAILED
        }
    }

    /**
     * 不入座、不移動，只回報目前可用的腳底位置。
     *
     * 與正式入座共用來源、占用及幾何檢查；執行時仍須重新驗證，不能把預檢當作座位預約。
     *
     * @param world 目標世界。
     * @param rider 乘坐者，可為尚未加入世界的自有角色。
     * @param request 固定席位。
     * @return 已確認可入座或安全站立的位置，無法保證時為 null。
     */
    fun preview(
        world: W,
        rider: R,
        request: SeatRequest,
    ): SeatGeometry? {
        val original = environment.snapshot(rider)
        var providerId = "unresolved"
        try {
            val relevant = inspectTargets(world, rider, request, original) { providerId = it }
            if (relevant.any { (_, target, state) -> state == SeatState.OCCUPIED || target.identity in claimed }) return null
            val seated = relevant.firstOrNull { it.third == SeatState.SEATED }
            if (seated != null) {
                val geometry = seated.second.geometry(world, rider)
                check(environment.unchanged(rider, original)) { "Seat provider changed rider during preview" }
                return geometry.takeIf { matches(it, request) && environment.hasVehicle(rider) && environment.positioned(world, rider, it) }
            }
            if (environment.hasVehicle(rider) || relevant.any { it.third == SeatState.FAILED }) return null
            val geometry = relevant.firstOrNull { it.third == SeatState.AVAILABLE }?.second?.geometry(world, rider)
                ?: request.seat.takeIf { environment.canStand(world, rider, request) }
            check(environment.unchanged(rider, original)) { "Seat provider changed rider during preview" }
            return geometry?.takeIf { matches(it, request) }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            if (!failurePolicy.isIsolatable(cause)) throw cause
            report(providerId, rider, cause)
            environment.recover(rider, original)
            return null
        }
    }

    /**
     * 只查詢真正的家具座位，不以安全站立位置代替。
     *
     * @param world 目標世界。
     * @param rider 要驗證的乘坐者。
     * @param request 固定席位。
     * @return 可用或已由本人乘坐的座位幾何；未知、占用或失敗時為 null。
     */
    fun previewSeat(
        world: W,
        rider: R,
        request: SeatRequest,
    ): SeatGeometry? = inspectSeat(world, rider, request).geometry

    /**
     * 唯讀檢查家具座位，保留「無法確認」與「確定沒有坐下」的差別。
     *
     * @param world 目標世界。
     * @param rider 要檢查的乘坐者。
     * @param request 固定席位。
     * @return 本次狀態；來源違約或拋錯時為 FAILED，不能據此移除既有參與者。
     */
    fun inspectSeat(
        world: W,
        rider: R,
        request: SeatRequest,
    ): SeatInspection {
        val original = environment.snapshot(rider)
        var providerId = "unresolved"
        try {
            val relevant = inspectTargets(world, rider, request, original) { providerId = it }
            if (relevant.any { it.third == SeatState.FAILED }) return SeatInspection(SeatState.FAILED, null)
            if (relevant.any { it.third == SeatState.OCCUPIED || it.second.identity in claimed }) return SeatInspection(SeatState.OCCUPIED, null)
            val seated = relevant.firstOrNull { it.third == SeatState.SEATED }
            val target = seated?.second ?: relevant.firstOrNull { it.third == SeatState.AVAILABLE }?.second
                ?: return SeatInspection(SeatState.INVALID, null)
            val geometry = target.geometry(world, rider)
            check(environment.unchanged(rider, original)) { "Seat provider changed rider during seat inspection" }
            if (!matches(geometry, request)) return SeatInspection(SeatState.INVALID, null)
            if (seated != null) {
                check(environment.hasVehicle(rider) && environment.positioned(world, rider, geometry)) { "Seat provider reported false seated state" }
                return SeatInspection(SeatState.SEATED, geometry)
            }
            return if (environment.hasVehicle(rider)) SeatInspection(SeatState.INVALID, null) else SeatInspection(SeatState.AVAILABLE, geometry)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            if (!failurePolicy.isIsolatable(cause)) throw cause
            report(providerId, rider, cause)
            environment.recover(rider, original)
            return SeatInspection(SeatState.FAILED, null)
        }
    }

    /**
     * 確認固定席位沒有已存在的座位，不讓新放置覆蓋空坐墊或其他家具。
     *
     * 與入座共用來源及幾何檢查，不移動乘坐者或預約席位。來源失敗時保守拒絕放置。
     *
     * @param world 目標世界。
     * @param rider 準備入座的角色。
     * @param request 固定席位。
     * @return 沒有有效座位目標時為 true。
     */
    fun isVacant(
        world: W,
        rider: R,
        request: SeatRequest,
    ): Boolean {
        val original = environment.snapshot(rider)
        var providerId = "unresolved"
        try {
            val relevant = inspectTargets(world, rider, request, original) { providerId = it }
            return relevant.all { it.third == SeatState.INVALID }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            if (!failurePolicy.isIsolatable(cause)) throw cause
            report(providerId, rider, cause)
            environment.recover(rider, original)
            return false
        }
    }

    /**
     * 查詢來源並確認查詢沒有移動乘坐者。
     *
     * @param world 目標世界。
     * @param rider 乘坐者。
     * @param request 固定席位。
     * @param original 查詢前的位置快照。
     * @param onProvider 保存目前來源供診斷。
     * @return 位置符合標準席位的來源與狀態。
     */
    private fun inspectTargets(
        world: W,
        rider: R,
        request: SeatRequest,
        original: S,
        onProvider: (String) -> Unit,
    ): List<Triple<String, SeatTarget<W, R>, SeatState>> {
        val candidates = providers.flatMap { registered ->
            onProvider(registered.id)
            registered.provider.resolve(world, request).map { registered.id to it }
        }
        val inspected = candidates.map { (id, target) ->
            onProvider(id)
            Triple(id, target, target.inspect(world, rider))
        }
        if (!environment.unchanged(rider, original)) error("Seat provider changed rider during resolution")
        val relevant = inspected.filter { (id, target, _) ->
            onProvider(id)
            target.identity.dimensionId == request.dimensionId && matches(target.geometry(world, rider), request)
        }
        if (!environment.unchanged(rider, original)) error("Seat provider changed rider during geometry lookup")
        return relevant
    }

    /**
     * 開局或局間換位時讓整桌入座：先讓正坐在本桌其他席位上的乘坐者站起來，全部站起後再依座位順序入座。
     *
     * 已坐在自己席位上的乘坐者不動；坐在本桌席位以外載具上的乘坐者不強制拉下來，入座結果照常回報
     * [SeatAssignmentResult.RIDING]。本元件需由平台在主執行緒呼叫。
     *
     * @param world 目標世界。
     * @param riders 依座位 index 排列的乘坐者；沒有可移動的實體（例如 AI 或離線玩家）時為 null。
     * @param requests 本桌所有固定席位，依座位 index 排列，數量與 [riders] 相同。
     * @param tableRequests 本桌所有等待席位，用來辨識可離開的本桌載具；預設為正式席位。
     * @return 依座位 index 排列的入座結果；沒有乘坐者的座位為 null。
     */
    fun seatAll(
        world: W,
        riders: List<R?>,
        requests: List<SeatRequest>,
        tableRequests: List<SeatRequest> = requests,
    ): List<SeatAssignmentResult?> {
        require(riders.size == requests.size) { "Every seat needs exactly one rider entry" }
        riders.forEachIndexed { seatIndex, rider -> rider?.let { leaveOtherTableSeat(world, it, tableRequests, requests[seatIndex]) } }
        return riders.mapIndexed { seatIndex, rider -> rider?.let { assign(world, it, requests[seatIndex]) } }
    }

    /**
     * 乘坐者正坐在本桌 [keepRequest] 以外的席位上時讓他站起來；查不到或坐在其他東西上時不動。
     *
     * @param world 目標世界。
     * @param rider 乘坐者。
     * @param requests 本桌所有固定席位，依座位 index 排列。
     * @param keepRequest 乘坐者這次要坐的正式座位。
     */
    private fun leaveOtherTableSeat(
        world: W,
        rider: R,
        requests: List<SeatRequest>,
        keepRequest: SeatRequest,
    ) {
        val original = environment.snapshot(rider)
        var providerId = "unresolved"
        try {
            val seatedElsewhere = requests.any { request ->
                request.seat != keepRequest.seat &&
                    providers.any { registered ->
                        providerId = registered.id
                        registered.provider.resolve(world, request).any { target ->
                            target.identity.dimensionId == request.dimensionId &&
                                matches(target.geometry(world, rider), request) &&
                                target.inspect(world, rider) == SeatState.SEATED
                        }
                    }
            }
            if (!environment.unchanged(rider, original)) error("Seat provider changed rider while looking up the current seat")
            if (seatedElsewhere) environment.leaveSeat(rider)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            if (!failurePolicy.isIsolatable(cause)) throw cause
            report(providerId, rider, cause)
            environment.recover(rider, original)
        }
    }

    /**
     * 檢查實際腳底位置與朝向，不接受附近的替代席位。
     *
     * @param geometry 提供者的實際乘坐點。
     * @param request 固定席位。
     * @return 水平偏差、朝向與高度均符合初版政策時為 true。
     */
    private fun matches(geometry: SeatGeometry, request: SeatRequest): Boolean {
        val seat = request.seat
        val dx = geometry.x - seat.x
        val dz = geometry.z - seat.z
        val delta = (geometry.yaw - seat.yaw) % FULL_TURN
        val angle = abs(((delta + FULL_TURN + HALF_TURN) % FULL_TURN) - HALF_TURN)
        return dx * dx + dz * dz <= HORIZONTAL_TOLERANCE * HORIZONTAL_TOLERANCE &&
            abs(geometry.y - seat.y) <= HEIGHT_TOLERANCE &&
            angle <= ANGLE_TOLERANCE
    }

    /** 固定席位的初版幾何限制。 */
    private companion object {
        /** 最大水平偏差。 */
        const val HORIZONTAL_TOLERANCE = 0.25

        /** 腳底與原站立高度的最大偏差，容許不同凳面但不接受高樓層座位。 */
        const val HEIGHT_TOLERANCE = 1.0

        /** 最大朝向偏差。 */
        const val ANGLE_TOLERANCE = 15f

        /** 一圈角度。 */
        const val FULL_TURN = 360f

        /** 半圈角度。 */
        const val HALF_TURN = 180f
    }
}
