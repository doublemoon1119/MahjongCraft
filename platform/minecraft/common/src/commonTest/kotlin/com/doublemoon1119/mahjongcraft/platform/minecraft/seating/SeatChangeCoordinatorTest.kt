package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatIdentity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.uuid.Uuid

/** 驗證開局與局間換位入座時，坐在本桌其他座位上的玩家會先起身，再依座位順序入座。 */
class SeatChangeCoordinatorTest {
    /** 本桌的兩個座位。 */
    private val seats = listOf(TableSeat(0), TableSeat(1))

    /** 本桌依座位 index 排列的固定席位。 */
    private val requests = seats.map { seat ->
        SeatRequest.create(
            tableId = TABLE_ID,
            seatIndex = seat.index,
            dimensionId = DIMENSION_ID,
            seat = seat.geometry,
        )
    }

    /** 兩位玩家互換座位時，先全部起身再入座，雙方都坐到新位置，不會因對方還坐著而被判定為占用。 */
    @Test
    fun `two seated players swap seats`() {
        val first = Rider()
        val second = Rider()
        seats[0].seat(first)
        seats[1].seat(second)
        val environment = Environment()

        val results = coordinator(environment).seatAll(World, listOf(second, first), requests)

        assertEquals(listOf(SeatAssignmentResult.SEATED, SeatAssignmentResult.SEATED), results)
        assertSame(seats[1], first.seat)
        assertSame(seats[0], second.seat)
        assertEquals(2, environment.leaveCalls)
    }

    /** 開局前坐在別人座位上的玩家移到自己的座位，被分到那個座位的人也坐得進去。 */
    @Test
    fun `a player sitting on another seat before the start moves to their own seat`() {
        val early = Rider()
        val late = Rider()
        seats[1].seat(early)
        val environment = Environment()

        val results = coordinator(environment).seatAll(World, listOf(early, late), requests)

        assertEquals(listOf(SeatAssignmentResult.SEATED, SeatAssignmentResult.SEATED), results)
        assertSame(seats[0], early.seat)
        assertSame(seats[1], late.seat)
    }

    /** 已坐在新座位上的玩家不起身也不移動。 */
    @Test
    fun `a player already in the new seat stays`() {
        val rider = Rider()
        seats[0].seat(rider)
        val environment = Environment()

        val results = coordinator(environment).seatAll(World, listOf(rider, null), requests)

        assertEquals(listOf(SeatAssignmentResult.SEATED, null), results)
        assertSame(seats[0], rider.seat)
        assertEquals(0, environment.leaveCalls)
    }

    /** 坐在本桌座位以外載具上的玩家不被拉下來，照常回報乘坐中。 */
    @Test
    fun `a player riding something else is not pulled off`() {
        val rider = Rider(ridingElsewhere = true)
        val environment = Environment()

        val results = coordinator(environment).seatAll(World, listOf(null, rider), requests)

        assertEquals(listOf(null, SeatAssignmentResult.RIDING), results)
        assertEquals(0, environment.leaveCalls)
    }

    /**
     * 建立只登記本桌座位的協調器。
     *
     * @param environment 測試用平台操作。
     * @return 協調器。
     */
    private fun coordinator(environment: Environment): SeatAssignmentCoordinator<World, Rider, Rider.Snapshot> = SeatAssignmentCoordinator(
        providers = listOf(
            RegisteredSeatProvider(
                id = "test:table",
                provider = SeatProvider { _, _ -> seats },
                priority = 0,
            ),
        ),
        environment = environment,
        failurePolicy = ExtensionFailurePolicy.Default,
    ) { _, _, _ -> }

    /** 測試用世界。 */
    private object World

    /**
     * 測試用玩家。
     *
     * @property ridingElsewhere 是否坐在本桌座位以外的載具上。
     */
    private class Rider(val ridingElsewhere: Boolean = false) {
        /** 目前坐的本桌座位。 */
        var seat: TableSeat? = null

        /**
         * 玩家位置快照。
         *
         * @property seat 快照時坐的座位。
         */
        data class Snapshot(val seat: TableSeat?)
    }

    /**
     * 會記住誰坐在上面的本桌座位。
     *
     * @property index 座位 index。
     */
    private class TableSeat(val index: Int) : SeatTarget<World, Rider> {
        /** 座位幾何，每個座位相隔兩格。 */
        val geometry = SeatGeometry(index * 2.0, 64.0, 0.0, 0.0f)

        /** 目前坐在上面的玩家。 */
        private var occupant: Rider? = null

        override val identity = SeatIdentity(DIMENSION_ID, "test:table", "seat", index.toString())

        override fun geometry(world: World, rider: Rider): SeatGeometry = geometry

        override fun inspect(world: World, rider: Rider): SeatState = when (occupant) {
            null -> SeatState.AVAILABLE
            rider -> SeatState.SEATED
            else -> SeatState.OCCUPIED
        }

        override fun trySit(world: World, rider: Rider): SeatState {
            if (occupant != null) return SeatState.OCCUPIED
            seat(rider)
            return SeatState.SEATED
        }

        /**
         * 讓玩家坐上這個座位。
         *
         * @param rider 玩家。
         */
        fun seat(rider: Rider) {
            occupant = rider
            rider.seat = this
        }

        /** 讓坐在上面的玩家起身。 */
        fun vacate() {
            occupant?.seat = null
            occupant = null
        }
    }

    /** 測試用平台操作。 */
    private class Environment : SeatEnvironment<World, Rider, Rider.Snapshot> {
        /** 讓玩家離開本桌座位的次數。 */
        var leaveCalls: Int = 0

        override fun snapshot(rider: Rider): Rider.Snapshot = Rider.Snapshot(rider.seat)

        override fun unchanged(rider: Rider, snapshot: Rider.Snapshot): Boolean = rider.seat === snapshot.seat

        override fun hasVehicle(rider: Rider): Boolean = rider.ridingElsewhere || rider.seat != null

        override fun stand(
            world: World,
            rider: Rider,
            request: SeatRequest,
        ): Boolean = false

        override fun positioned(
            world: World,
            rider: Rider,
            geometry: SeatGeometry,
        ): Boolean = rider.seat?.geometry == geometry

        override fun leaveSeat(rider: Rider) {
            leaveCalls++
            rider.seat?.vacate()
        }

        override fun recover(rider: Rider, snapshot: Rider.Snapshot) = Unit
    }

    private companion object {
        /** 測試桌子。 */
        val TABLE_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

        /** 測試維度。 */
        const val DIMENSION_ID: String = "minecraft:overworld"
    }
}
