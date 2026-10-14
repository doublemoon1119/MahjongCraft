package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatIdentity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatTarget
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證固定席位協調器的占用、安全空間、失敗回復與泛型乘坐者契約。 */
class SeatAssignmentCoordinatorTest {
    /** 測試用固定世界實例。 */
    private val world = FakeWorld()

    /** 空位可被一般非玩家乘坐者安全使用。 */
    @Test
    fun `available seat accepts a non player rider`() {
        val rider = FakeRider()
        val target = FakeTarget("seat")
        val environment = FakeEnvironment()

        val result = coordinator(target, environment).assign(world, rider, request())

        assertEquals(SeatAssignmentResult.SEATED, result)
        assertTrue(rider.seated)
    }

    /** 本人已經在固定座位時不重複傳送或建立乘坐關係。 */
    @Test
    fun `already seated rider is left in place`() {
        val rider = FakeRider(seated = true)
        val target = FakeTarget("seat", state = SeatState.SEATED)
        val environment = FakeEnvironment()

        val result = coordinator(target, environment).assign(world, rider, request())

        assertEquals(SeatAssignmentResult.SEATED, result)
        assertEquals(0, target.trySitCalls)
        assertEquals(0, environment.standCalls)
    }

    /** 他人占用座位時不搶位，也不在同一位置改成站立。 */
    @Test
    fun `occupied seat is not stolen`() {
        val rider = FakeRider()
        val target = FakeTarget("seat", state = SeatState.OCCUPIED)
        val environment = FakeEnvironment()

        val result = coordinator(target, environment).assign(world, rider, request())

        assertEquals(SeatAssignmentResult.OCCUPIED, result)
        assertEquals(0, environment.standCalls)
        assertEquals(0, target.trySitCalls)
    }

    /** 座位不可用但固定席位可安全站立時才傳送。 */
    @Test
    fun `unavailable seat falls back to safe standing`() {
        val rider = FakeRider()
        val target = FakeTarget("seat", state = SeatState.UNSUPPORTED)
        val environment = FakeEnvironment(canStand = true)

        val result = coordinator(target, environment).assign(world, rider, request())

        assertEquals(SeatAssignmentResult.STANDING, result)
        assertEquals(1, environment.standCalls)
    }

    /** 固定席位沒有支撐或身體空間時保留原位。 */
    @Test
    fun `blocked standing position keeps rider in place`() {
        val rider = FakeRider()
        val target = FakeTarget("seat", state = SeatState.BLOCKED)
        val environment = FakeEnvironment(canStand = false)

        val result = coordinator(target, environment).assign(world, rider, request())

        assertEquals(SeatAssignmentResult.BLOCKED, result)
        assertEquals(1, environment.standCalls)
        assertEquals(0, environment.recoverCalls)
    }

    /** 乘坐者已經有其他載具時不強制卸乘。 */
    @Test
    fun `existing unrelated vehicle prevents automatic seating`() {
        val rider = FakeRider(vehicle = true)
        val target = FakeTarget("seat")
        val environment = FakeEnvironment()

        val result = coordinator(target, environment).assign(world, rider, request())

        assertEquals(SeatAssignmentResult.RIDING, result)
        assertEquals(0, target.trySitCalls)
    }

    /** 執行前座位被占用時，第二次檢查阻止入座。 */
    @Test
    fun `seat occupied between inspection and sit is rejected`() {
        val rider = FakeRider()
        val target = FakeTarget("seat", occupyOnSecondInspection = true)
        val environment = FakeEnvironment()

        val result = coordinator(target, environment).assign(world, rider, request())

        assertEquals(SeatAssignmentResult.OCCUPIED, result)
        assertEquals(0, target.trySitCalls)
    }

    /** 同一席位被不同 provider 回報時，已占用目標仍以保守結果處理。 */
    @Test
    fun `duplicate provider target remains occupied`() {
        val rider = FakeRider()
        val occupied = FakeTarget("shared", state = SeatState.OCCUPIED)
        val available = FakeTarget("shared")
        val environment = FakeEnvironment()
        val providers = listOf(
            provider("first") { _, _ -> listOf(occupied) },
            provider("second") { _, _ -> listOf(available) },
        )

        val result = SeatAssignmentCoordinator(providers, environment) { _, _ -> }
            .assign(world, rider, request())

        assertEquals(SeatAssignmentResult.OCCUPIED, result)
        assertEquals(0, available.trySitCalls)
    }

    /** 多座位家具的不同 slot 可被獨立識別。 */
    @Test
    fun `multi seat targets have independent identities`() {
        val rider = FakeRider()
        val left = FakeTarget("bench", slot = "left")
        val right = FakeTarget("bench", slot = "right")

        assertTrue(left.identity != right.identity)
        val result = SeatAssignmentCoordinator(
            providers = listOf(provider("bench") { _, _ -> listOf(left, right) }),
            environment = FakeEnvironment(),
        ) { _, _ -> }.assign(world, rider, request())

        assertEquals(SeatAssignmentResult.SEATED, result)
        assertEquals(1, left.trySitCalls)
        assertEquals(0, right.trySitCalls)
    }

    /** 第二位乘坐者即使收到同一座位的空位回報，也不能重複認領已使用的識別。 */
    @Test
    fun `claimed identity rejects a second rider`() {
        val firstRider = FakeRider()
        val secondRider = FakeRider()
        val firstTarget = FakeTarget("shared", slot = "main")
        val secondTarget = FakeTarget("shared", slot = "main")
        val environment = FakeEnvironment()
        val coordinator = SeatAssignmentCoordinator(
            providers = listOf(provider("shared") { _, _ -> listOf(firstTarget, secondTarget) }),
            environment = environment,
        ) { _, _ -> }

        assertEquals(SeatAssignmentResult.SEATED, coordinator.assign(world, firstRider, request()))
        assertEquals(SeatAssignmentResult.OCCUPIED, coordinator.assign(world, secondRider, request()))
        assertEquals(0, secondTarget.trySitCalls)
    }

    /** 多座位家具的其他占用 slot 不應遮蔽符合本席位的空位。 */
    @Test
    fun `occupied slot outside placement does not block matching slot`() {
        val rider = FakeRider()
        val occupied = FakeTarget("bench", state = SeatState.OCCUPIED, slot = "left", x = 1.0)
        val available = FakeTarget("bench", slot = "right")
        val coordinator = SeatAssignmentCoordinator(
            providers = listOf(provider("bench") { _, _ -> listOf(occupied, available) }),
            environment = FakeEnvironment(),
        ) { _, _ -> }

        val result = coordinator.assign(world, rider, request())

        assertEquals(SeatAssignmentResult.SEATED, result)
        assertEquals(1, available.trySitCalls)
    }

    /** provider 解析失敗時採 fail-closed，下一次獨立嘗試仍可正常解析。 */
    @Test
    fun `provider resolution failure does not stand and later retry works`() {
        val rider = FakeRider()
        val target = FakeTarget("retry")
        var attempts = 0
        val environment = FakeEnvironment(canStand = true)
        val coordinator = SeatAssignmentCoordinator(
            providers = listOf(
                provider("retry") { _, _ ->
                    attempts++
                    if (attempts == 1) throw IllegalStateException("temporary provider failure")
                    listOf(target)
                },
            ),
            environment = environment,
        ) { _, _ -> }

        assertEquals(SeatAssignmentResult.FAILED, coordinator.assign(world, rider, request()))
        assertEquals(0, environment.standCalls)
        assertEquals(SeatAssignmentResult.SEATED, coordinator.assign(world, rider, request()))
    }

    /** 幾何位置偏離固定席位時不會改坐附近目標。 */
    @Test
    fun `geometry outside fixed placement falls back to standing`() {
        val rider = FakeRider()
        val target = FakeTarget("seat", x = 2.0)
        val environment = FakeEnvironment(canStand = true)

        val result = coordinator(target, environment).assign(world, rider, request())

        assertEquals(SeatAssignmentResult.STANDING, result)
        assertEquals(0, target.trySitCalls)
    }

    /** 朝向比較會正規化負數多圈角度，仍能辨識同一固定席位。 */
    @Test
    fun `negative multi turn yaw matches fixed placement`() {
        val rider = FakeRider()
        val target = FakeTarget("seat", yaw = -720.0f)

        val result = coordinator(target, FakeEnvironment()).assign(world, rider, request())

        assertEquals(SeatAssignmentResult.SEATED, result)
    }

    /** 提供者違反失敗契約時回復乘坐者並回報錯誤。 */
    @Test
    fun `provider violation is recovered and reported`() {
        val rider = FakeRider()
        val target = FakeTarget("bad", failedSitMovesRider = true)
        val environment = FakeEnvironment()
        val reports = mutableListOf<String>()

        val result = SeatAssignmentCoordinator(
            providers = listOf(provider("bad") { _, _ -> listOf(target) }),
            environment = environment,
        ) { id, _ -> reports += id }.assign(world, rider, request())

        assertEquals(SeatAssignmentResult.FAILED, result)
        assertEquals(1, environment.recoverCalls)
        assertEquals(listOf("test:bad"), reports)
    }

    /** provider 虛報 SEATED 卻未建立乘坐關係時，協調器必須拒絕假成功。 */
    @Test
    fun `seated result without a riding relationship is rejected`() {
        val rider = FakeRider()
        val target = FakeTarget("false-success", reportsSeatedWithoutState = true)
        val environment = FakeEnvironment()

        val result = coordinator(target, environment).assign(world, rider, request())

        assertEquals(SeatAssignmentResult.FAILED, result)
        assertEquals(1, environment.recoverCalls)
    }

    /** 取消例外必須外傳，不被座位協調器轉為一般失敗。 */
    @Test
    fun `cancellation is propagated`() {
        val rider = FakeRider()
        val provider = provider("cancel") { _, _ -> throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> {
            SeatAssignmentCoordinator(listOf(provider), FakeEnvironment()) { _, _ -> }
                .assign(world, rider, request())
        }
    }

    /** 建立通用的固定席位請求。 */
    private fun request(): SeatRequest = SeatRequest(
        tableId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
        seatIndex = 0,
        dimensionId = "minecraft:overworld",
        placement = MahjongSeatPlacement(0.0, 64.0, 0.0, 0.0f),
    )

    /** 建立帶有固定 provider ID 的測試 provider。 */
    private fun provider(
        id: String,
        resolve: (FakeWorld, SeatRequest) -> List<SeatTarget<FakeWorld, FakeRider>>,
    ): RegisteredSeatProvider<FakeWorld, FakeRider> = RegisteredSeatProvider(
        id = "test:$id",
        provider = SeatProvider { world, request -> resolve(world, request) },
        priority = 0,
    )

    /** 建立單一測試 target 的協調器。 */
    private fun coordinator(target: FakeTarget, environment: FakeEnvironment): SeatAssignmentCoordinator<FakeWorld, FakeRider, Snapshot> = SeatAssignmentCoordinator(
        providers = listOf(provider("test") { _, _ -> listOf(target) }),
        environment = environment,
    ) { _, _ -> }

    /** 測試用的世界型別。 */
    private class FakeWorld

    /**
     * 測試用的乘坐者型別，可代表玩家或一般實體。
     *
     * @property position 測試用位置索引。
     * @property seated 是否已建立座位乘坐關係。
     * @property vehicle 是否已存在其他載具。
     */
    private class FakeRider(
        var position: Int = 0,
        var seated: Boolean = false,
        var vehicle: Boolean = false,
    )

    /**
     * 測試用的原位置快照。
     *
     * @property position 測試用位置索引。
     * @property seated 原位置是否已有座位乘坐關係。
     */
    private data class Snapshot(val position: Int, val seated: Boolean)

    /**
     * 測試用平台安全操作。
     *
     * @property canStand 固定席位是否有安全站立空間。
     */
    private class FakeEnvironment(
        private val canStand: Boolean = false,
    ) : SeatEnvironment<FakeWorld, FakeRider, Snapshot> {
        /** 呼叫安全站立的次數。 */
        var standCalls: Int = 0

        /** 呼叫安全回復的次數。 */
        var recoverCalls: Int = 0

        override fun snapshot(rider: FakeRider): Snapshot = Snapshot(rider.position, rider.seated)

        override fun unchanged(rider: FakeRider, snapshot: Snapshot): Boolean = rider.position == snapshot.position && rider.seated == snapshot.seated

        override fun hasVehicle(rider: FakeRider): Boolean = rider.vehicle || rider.seated

        override fun stand(
            world: FakeWorld,
            rider: FakeRider,
            request: SeatRequest,
        ): Boolean {
            standCalls++
            if (canStand) rider.position = 1
            return canStand
        }

        override fun positioned(
            world: FakeWorld,
            rider: FakeRider,
            geometry: SeatGeometry,
        ): Boolean = rider.seated

        override fun recover(rider: FakeRider, snapshot: Snapshot) {
            recoverCalls++
            rider.position = snapshot.position
            rider.seated = snapshot.seated
        }
    }

    /**
     * 測試用座位 target，可模擬競態與違約 provider。
     *
     * @param key 座位識別 key。
     * @param state 初始座位狀態。
     * @param slot 多座位家具的子座位 key。
     * @property x 測試用乘坐位置 X 座標。
     * @property yaw 測試用乘坐朝向。
     * @property occupyOnSecondInspection 第二次檢查時是否模擬被占用。
     * @property failedSitMovesRider 失敗時是否模擬違約移動乘坐者。
     * @property reportsSeatedWithoutState 是否回報入座但不建立乘坐關係。
     */
    private class FakeTarget(
        key: String,
        state: SeatState = SeatState.AVAILABLE,
        slot: String = "main",
        private val x: Double = 0.0,
        private val yaw: Float = 0.0f,
        private val occupyOnSecondInspection: Boolean = false,
        private val failedSitMovesRider: Boolean = false,
        private val reportsSeatedWithoutState: Boolean = false,
    ) : SeatTarget<FakeWorld, FakeRider> {
        override val identity = SeatIdentity("minecraft:overworld", "test:seat", key, slot)
        private var currentState = state
        private var inspections = 0

        /** 呼叫嘗試入座的次數。 */
        var trySitCalls: Int = 0

        override fun geometry(world: FakeWorld, rider: FakeRider): SeatGeometry = SeatGeometry(x, 64.0, 0.0, yaw)

        override fun inspect(world: FakeWorld, rider: FakeRider): SeatState {
            inspections++
            if (occupyOnSecondInspection && inspections >= 2) currentState = SeatState.OCCUPIED
            return currentState
        }

        override fun trySit(world: FakeWorld, rider: FakeRider): SeatState {
            trySitCalls++
            if (failedSitMovesRider) {
                rider.position = 99
                return SeatState.FAILED
            }
            if (currentState != SeatState.AVAILABLE) return currentState
            if (!reportsSeatedWithoutState) rider.seated = true
            currentState = SeatState.SEATED
            return SeatState.SEATED
        }
    }
}
