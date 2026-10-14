package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatIdentity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
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
 * @property report 提供者失敗的診斷；傳入提供者 ID 與例外。
 */
class SeatAssignmentCoordinator<W, R, S>(
    private val providers: List<RegisteredSeatProvider<W, R>>,
    private val environment: SeatEnvironment<W, R, S>,
    private val report: (String, Throwable) -> Unit,
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
            val candidates = providers.flatMap { registered ->
                providerId = registered.id
                registered.provider.resolve(world, request).map { registered.id to it }
            }
            val inspected = candidates.map { (id, target) ->
                providerId = id
                Triple(id, target, target.inspect(world, rider))
            }
            if (!environment.unchanged(rider, original)) error("Seat provider changed rider during resolution")
            val relevant = inspected.filter { (id, target, _) ->
                providerId = id
                target.identity.dimensionId == request.dimensionId && matches(target.geometry(world, rider), request)
            }
            if (!environment.unchanged(rider, original)) error("Seat provider changed rider during geometry lookup")
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
        } catch (cause: Exception) {
            report(providerId, cause)
            environment.recover(rider, original)
            return SeatAssignmentResult.FAILED
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
        val placement = request.placement
        val dx = geometry.x - placement.x
        val dz = geometry.z - placement.z
        val delta = (geometry.yaw - placement.yaw) % FULL_TURN
        val angle = abs(((delta + FULL_TURN + HALF_TURN) % FULL_TURN) - HALF_TURN)
        return dx * dx + dz * dz <= HORIZONTAL_TOLERANCE * HORIZONTAL_TOLERANCE &&
            abs(geometry.y - placement.y) <= HEIGHT_TOLERANCE &&
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
