@file:OptIn(ExperimentalAtomicApi::class)

package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.flow.server.observer.ObserverSnapshotBroadcaster
import com.doublemoon1119.mahjongcraft.flow.server.room.RoomRosterChangePolicy
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.JoinExternalRoomParticipantUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.LeaveRoomUseCase
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricMahjongRooms
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricRoomParticipant
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricRoomVacancy
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricRoomStartSeating
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatEnvironment
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatingSessionFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterParticipantCapture
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.RoomDiscoveryBudget
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.SeatedParticipantObservation
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.SeatedParticipantSources
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.SeatedParticipantStartPreflight
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.SeatedParticipantPresence
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.DimensionChunkKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableInteractionReach
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.TranslatableTextContent
import net.minecraft.util.TypeFilter
import net.minecraft.util.math.Box
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * 將已入座的登記實體收斂為房間成員，並提供不載入區塊的授權空席查詢。
 *
 * @property persistence 同一提交的房間與外觀資料。
 * @property participants 已凍結的實體辨識能力。
 * @property characters 已登記角色描述。
 * @property capabilities 已登記世界角色能力。
 * @property strategies 可使用的策略。
 * @property sessions 共用固定席位與 provider 驗證。
 * @property locations 已載入桌子的位置索引。
 * @property bindings 已由遊戲接管的實體，避免重複認領。
 * @property rosterPolicy 房間名冊的額外限制。
 * @property join 權威外部成員加入交易。
 * @property broadcaster 既有觀察者公開資料交付。
 * @property leave 權威退出操作。
 * @property scope 目前 session 的協程。
 * @property dispatchers 權威操作所在調度器。
 * @property failurePolicy 擴充例外隔離規則。
 */
internal class FabricSeatedParticipantService(
    private val persistence: FabricAuthoritativeStatePersistence,
    private val participants: FabricRoomParticipantRegistry,
    private val characters: AiCharacterRegistry,
    private val capabilities: FabricAiCharacterCapabilityRegistry,
    private val strategies: MahjongAiStrategyRegistry,
    private val sessions: FabricSeatingSessionFactory,
    private val locations: TableLocationRegistry,
    private val bindings: FabricAiCharacterBindings,
    private val rosterPolicy: RoomRosterChangePolicy,
    private val join: JoinExternalRoomParticipantUseCase,
    private val broadcaster: ObserverSnapshotBroadcaster,
    private val leave: LeaveRoomUseCase,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val failurePolicy: ExtensionFailurePolicy,
) {
    /** 不含私人牌局資訊的擴充診斷。 */
    private val logger = mahjongCraftLogger(FabricSeatedParticipantService::class)

    /** 目前接收工作的 session。 */
    @Volatile
    private var server: MinecraftServer? = null

    /** 每次只允許一份收斂工作。 */
    private val activeScan = AtomicReference<Any?>(null)

    /** 停止 session 時取消仍在等待權威交易的掃描。 */
    private var scanJob: Job? = null

    /** 已確認離開的來源與原房間生命週期，卸載不建立此記錄。 */
    private val departures = mutableMapOf<RoomParticipantIdentity, Uuid>()

    /** 分散不同房間的掃描起點。 */
    private var cursor = 0

    /** 同一 session 的有界主體與全域查詢額度，不快取授權或結果。 */
    private val queryBudget = RoomDiscoveryBudget(QUERY_INTERVAL, QUERIES_PER_TICK, MAX_QUERY_SUBJECTS)

    /** 防止擴充回呼重入查詢。 */
    private var querying = false

    /** 已回報的能力錯誤；每個能力僅留一份摘要。 */
    private val reported = mutableMapOf<String, String>()

    /**
     * 追蹤改變時沿既有授權廣播刷新參照，不另外建立桌子查詢通道。
     *
     * @param entity 追蹤狀態剛變動的角色。
     */
    private fun refreshTrackedPreview(entity: Entity) {
        val current = server ?: return
        if (entity.world.server !== current) return
        val state = persistence.characterSnapshot() ?: return
        val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, entity.uuid.toKotlinUuid())
        val tableId = state.claimedParticipants[identity] ?: bindings.coordinator.keyFor(entity.uuid.toKotlinUuid())?.tableId ?: return
        broadcaster.requestRefresh(tableId)
    }

    /** 登記固定頻率的有界收斂，不等待所有房間。 */
    fun registerEvents() {
        EntityTrackingEvents.START_TRACKING.register { entity, _ -> refreshTrackedPreview(entity) }
        EntityTrackingEvents.STOP_TRACKING.register { entity, _ -> refreshTrackedPreview(entity) }
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, world ->
            if (server !== world.server || !FabricParticipantRemovalPolicy.isConfirmedDeparture(entity.removalReason)) return@register
            val state = persistence.characterSnapshot() ?: return@register
            val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, entity.uuid.toKotlinUuid())
            val tableId = state.claimedParticipants[identity] ?: return@register
            val waiting = state.authoritative.rooms[tableId] ?: return@register
            departures[identity] = waiting.admission.instanceId
        }
        ServerTickEvents.END_SERVER_TICK.register { current ->
            if (server !== current || activeScan.load() != null || current.ticks % SCAN_INTERVAL != 0) return@register
            val state = persistence.characterSnapshot() ?: return@register
            departures.entries.removeAll { (identity, instance) ->
                val tableId = state.claimedParticipants[identity]
                state.authoritative.rooms[tableId]?.admission?.instanceId != instance
            }
            val rooms = state.authoritative.rooms.values.toList()
            if (rooms.isEmpty()) return@register
            val scan = Any()
            if (!activeScan.compareAndSet(null, scan)) return@register
            val batch = List(min(ROOMS_PER_SCAN, rooms.size)) { rooms[(cursor + it) % rooms.size] }
            if (rooms.isNotEmpty()) cursor = (cursor + batch.size) % rooms.size
            val job = scope.launch(dispatchers.main, start = CoroutineStart.LAZY) {
                batch.forEach { room ->
                    if (server === current && activeScan.load() === scan) {
                        try {
                            refreshExcludedNames(room, current)
                            reconcile(room, current)
                        } catch (cause: Throwable) {
                            if (cause is CancellationException || !failurePolicy.isIsolatable(cause)) throw cause
                            logger.error("Seated participant reconciliation failed roomId={}", room.id, cause)
                        }
                    }
                }
            }
            scanJob = job
            job.invokeOnCompletion { activeScan.compareAndSet(scan, null) }
            job.start()
        }
    }

    /**
     * 開始本次 session 的唯讀查詢。
     *
     * @param current 已還原權威資料的伺服器。
     */
    fun startSession(current: MinecraftServer) {
        stopSession()
        server = current
        FabricMahjongRooms.install(::findVacancies)
    }

    /** 停止接收工作並清除前一世界的查詢與診斷，不移動實體。 */
    fun stopSession() {
        FabricMahjongRooms.install(null)
        server = null
        scanJob?.cancel()
        scanJob = null
        activeScan.store(null)
        cursor = 0
        departures.clear()
        queryBudget.reset()
        querying = false
        reported.clear()
    }

    /**
     * 查詢此實體當下可加入的附近席位，沒有結果時不揭露其他房間。
     *
     * @param entity 呼叫端提供、仍由伺服器驗證的實體。
     * @param requestedDistance 呼叫端希望採用的更小距離。
     * @return 有界的當下提示，不是預約。
     */
    private fun findVacancies(entity: Entity, requestedDistance: Double): List<FabricRoomVacancy> {
        val current = server ?: return emptyList()
        check(current.isOnThread) { "Room discovery requires the server thread" }
        val world = entity.world as? ServerWorld ?: return emptyList()
        if (world.server !== current || !validEntity(entity, world) || querying || !requestedDistance.isFinite() || requestedDistance < 0) return emptyList()
        val state = persistence.characterSnapshot() ?: return emptyList()
        if (state.characters == null) return emptyList()
        val id = entity.uuid.toKotlinUuid()
        val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, id)
        if (isClaimed(identity)) return emptyList()
        val tick = current.ticks.toLong()
        if (!queryBudget.acquire(id, tick)) return emptyList()
        querying = true
        return try {
            val range = min(requestedDistance, TableInteractionReach.SURVIVAL)
            val entries = (-1..1).asSequence().flatMap { x ->
                (-1..1).asSequence().map { z ->
                    DimensionChunkKey(world.registryKey.value.toString(), (entity.blockX shr 4) + x, (entity.blockZ shr 4) + z)
                }
            }.flatMap { locations.getByChunk(it, TABLES_PER_QUERY).asSequence() }.take(TABLES_PER_QUERY)
            entries.flatMap { entry ->
                val location = entry.location
                if (!world.chunkManager.isChunkLoaded(location.chunkX, location.chunkZ) ||
                    TableInteractionReach.distance(entity.x, entity.y, entity.z, location.x, location.y, location.z) > range
                ) {
                    return@flatMap emptySequence()
                }
                val room = state.authoritative.rooms[entry.tableId] ?: return@flatMap emptySequence()
                if (room.isFull || identity in room.admission.excludedParticipants || describe(entity, room.hostId) !is ParticipantCheck.Eligible) return@flatMap emptySequence()
                val proposed = room.copy(playerIds = room.playerIds + id)
                if (!rosterPolicy.allows(room, proposed)) return@flatMap emptySequence()
                val seating = sessions.prepareTable(room.id) ?: return@flatMap emptySequence()
                seating.requests.asSequence().mapIndexedNotNull { index, request ->
                    val geometry = seating.coordinator.previewSeat(world, entity, request) ?: return@mapIndexedNotNull null
                    FabricRoomVacancy.create(room.id, room.admission.instanceId, index, geometry, true)
                }
            }.take(RESULTS_PER_QUERY).toList()
        } finally {
            querying = false
        }
    }

    /**
     * 更新房主已能看見的排除實體名稱；未載入、未追蹤或授權失效時保留最後名稱。
     *
     * @param room 本輪仍有效的房間。
     * @param current 目前伺服器 session。
     */
    private suspend fun refreshExcludedNames(room: Room, current: MinecraftServer) {
        val host = current.playerManager.getPlayer(room.hostId.toJavaUuid()) ?: return
        val location = locations.get(room.id)?.location ?: return
        if (host.serverWorld.registryKey.value.toString() != location.dimensionId) return
        for ((identity, member) in room.admission.excludedParticipants) {
            if (server !== current || identity.sourceId != SeatedParticipantSources.WORLD_ENTITY) continue
            val entity = host.serverWorld.getEntity(identity.subjectId.toJavaUuid()) ?: continue
            if (!entity.isAlive || host !in PlayerLookup.tracking(entity)) continue
            val eligible = describe(entity, room.hostId) as? ParticipantCheck.Eligible ?: continue
            val envelope = persistence.characterSnapshot() ?: return
            val previous = envelope.characters?.participant(member) ?: continue
            val appearance = previous.copy(displayName = captureAppearance(entity, eligible.participant.character).displayName)
            if (appearance == previous) continue
            val capture = AiCharacterParticipantCapture(room.admission.instanceId, member, identity, appearance)
            if (persistence.refreshParticipant(room, envelope.sessionId, capture)) broadcaster.requestRefresh(room.id)
        }
    }

    /**
     * 複核已綁定成員，再從可形成合法布局的固定席位加入新成員。
     *
     * @param expected 這輪掃描起點的房間。
     * @param current 發起掃描的 session。
     */
    private suspend fun reconcile(expected: Room, current: MinecraftServer) {
        val envelope = persistence.characterSnapshot() ?: return
        if (envelope.characters == null || envelope.authoritative.rooms[expected.id] != expected) return
        for ((playerId, identity) in expected.admission.memberIdentities) {
            if (departures[identity] == expected.admission.instanceId) {
                if (server === current) leave(expected.id, playerId, expectedRoom = expected)
                if (departures[identity] == expected.admission.instanceId) departures.remove(identity)
                return
            }
        }
        val tableSeats = sessions.prepareTable(expected.id) ?: return
        for ((playerId, identity) in expected.admission.memberIdentities) {
            if (identity.sourceId != SeatedParticipantSources.WORLD_ENTITY) continue
            val entity = tableSeats.world.getEntity(identity.subjectId.toJavaUuid())
            if (entity == null || !entity.isAlive) {
                if (server === current) leave(expected.id, playerId, expectedRoom = expected)
                return
            }
            if (bindings.coordinator.keyFor(entity.uuid.toKotlinUuid()) != null) continue
            val checks = tableSeats.requests.map { tableSeats.coordinator.inspectSeat(tableSeats.world, entity, it).state }
            val participant = describe(entity, expected.hostId)
            if (participant == ParticipantCheck.Unavailable || SeatedParticipantPresence.resolve(checks) == SeatedParticipantPresence.UNKNOWN) continue
            if (SeatedParticipantPresence.resolve(checks) == SeatedParticipantPresence.PRESENT && participant is ParticipantCheck.Eligible) {
                val appearance = captureAppearance(entity, participant.participant.character)
                val capture = AiCharacterParticipantCapture(expected.admission.instanceId, playerId, identity, appearance)
                val envelope = persistence.characterSnapshot() ?: return
                val changed = envelope.characters?.participant(playerId) != appearance
                if (persistence.refreshParticipant(expected, envelope.sessionId, capture) && changed) broadcaster.requestRefresh(expected.id)
            } else {
                if (server === current && persistence.characterSnapshot()?.authoritative?.rooms?.get(expected.id) == expected) leave(expected.id, playerId, expectedRoom = expected)
                return
            }
        }
        if (expected.isFull || expected.playerIds.size !in 1..3) return
        val aiId = Uuid.random()
        val order = expected.playerIds + aiId
        if (!rosterPolicy.allows(expected, expected.copy(playerIds = order))) return
        val seating = sessions.prepareTable(expected.id) ?: return
        for (request in seating.requests) {
            val position = request.seat
            val candidates = mutableListOf<Entity>()
            seating.world.collectEntitiesByType(
                TypeFilter.instanceOf(Entity::class.java),
                Box(position.x - 1.0, position.y - 2.0, position.z - 1.0, position.x + 1.0, position.y + 2.0, position.z + 1.0),
                { true },
                candidates,
                CANDIDATES_PER_SEAT + 1,
            )
            if (candidates.size > CANDIDATES_PER_SEAT) continue
            val eligible = candidates.mapNotNull { entity ->
                if (!entity.hasVehicle() || !validEntity(entity, seating.world) || seating.coordinator.inspectSeat(seating.world, entity, request).state != SeatState.SEATED) return@mapNotNull null
                val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, entity.uuid.toKotlinUuid())
                if (identity in expected.admission.excludedParticipants || isClaimed(identity)) return@mapNotNull null
                val participant = (describe(entity, expected.hostId) as? ParticipantCheck.Eligible)?.participant ?: return@mapNotNull null
                Triple(entity, identity, participant)
            }
            if (eligible.size != 1) continue
            val (entity, identity, participant) = eligible.single()
            if (server !== current ||
                persistence.characterSnapshot()?.authoritative?.rooms?.get(expected.id) != expected ||
                !entity.hasVehicle() ||
                seating.coordinator.inspectSeat(seating.world, entity, request).state != SeatState.SEATED
            ) {
                return
            }
            currentCoroutineContext().ensureActive()
            val appearance = captureAppearance(entity, participant.character)
            val capture = AiCharacterParticipantCapture(expected.admission.instanceId, aiId, identity, appearance)
            persistence.withParticipant(
                capture = capture,
                validate = {
                    server === current &&
                        validEntity(entity, seating.world) &&
                        entity.hasVehicle() &&
                        seating.coordinator.inspectSeat(seating.world, entity, request).state == SeatState.SEATED &&
                        (describe(entity, expected.hostId) as? ParticipantCheck.Eligible)?.participant?.let {
                            it.strategyKey == participant.strategyKey && captureAppearance(entity, it.character) == appearance
                        } == true
                },
                rejected = { Outcome.Error(RoomError.RoomLayoutConflict(expected.id)) },
            ) { join(expected, aiId, identity, participant.strategyKey, order) }
            return
        }
    }

    /**
     * 按規則已決定的正式名冊準備本次入座，不重新擲骰或重排。
     *
     * @param room 仍在等待的原房間。
     * @param playerIds 本次規則產生的正式座位順序。
     * @return 可安排並在失敗時恢復的入座操作；資格或席位不可確認時為 null。
     */
    fun startSeating(room: Room, playerIds: List<Uuid>): FabricRoomStartSeating? {
        val current = server ?: return null
        if (playerIds.size != room.playerIds.size || playerIds.toSet() != room.playerIds.toSet() || prepareStart(room) == null) return null
        val table = sessions.prepareTable(room.id) ?: return null
        val seating = sessions.prepare(room.id, playerIds) ?: return null
        val riders = playerIds.map { player ->
            val identity = room.admission.memberIdentities[player]
            if (identity?.sourceId == SeatedParticipantSources.WORLD_ENTITY) {
                table.world.getEntity(identity.subjectId.toJavaUuid())
            } else if (player in room.aiPlayerIds) {
                null
            } else {
                current.playerManager.getPlayer(player.toJavaUuid())
            }
        }
        if (playerIds.indices.any { playerIds[it] !in room.aiPlayerIds && riders[it] == null }) return null
        val borrowed = playerIds.indices.filter { room.admission.memberIdentities[playerIds[it]]?.sourceId == SeatedParticipantSources.WORLD_ENTITY }.mapNotNull { riders[it] }.toSet()
        if (borrowed.size != room.admission.memberIdentities.size) return null
        return FabricRoomStartSeating(
            riders = riders,
            session = seating,
            table = table,
        )
    }

    /**
     * 提交前核對借用角色已位於正式席位，並重新確認來源、外觀及安全狀態。
     *
     * @param room 本次預檢的等待房間。
     * @param playerIds 已計算且不再重排的正式名冊。
     * @param captures 入座前已擷取的來源與外觀。
     * @return 全部角色在正式席位乘坐或安全站立時為 true。
     */
    fun startSeatsMatch(
        room: Room,
        playerIds: List<Uuid>,
        captures: List<AiCharacterParticipantCapture>,
    ): Boolean {
        val seating = sessions.prepare(room.id, playerIds) ?: return false
        if (captures.size != room.admission.memberIdentities.size) return false
        return captures.all { capture ->
            val identity = room.admission.memberIdentities[capture.playerId]
            val index = playerIds.indexOf(capture.playerId)
            val entity = seating.world.getEntity(capture.identity.subjectId.toJavaUuid())
            if (identity != capture.identity || index < 0 || entity == null || !validEntity(entity, seating.world)) return@all false
            val eligible = describe(entity, room.hostId) as? ParticipantCheck.Eligible ?: return@all false
            if (captureAppearance(entity, eligible.participant.character) != capture.appearance) return@all false
            val geometry = seating.coordinator.preview(seating.world, entity, seating.requests[index]) ?: return@all false
            FabricSeatEnvironment().positioned(seating.world, entity, geometry)
        }
    }

    /**
     * 開局前複核外部成員仍在同一個有效席位，未知資料保守拒絕。
     *
     * @param room 即將開局的完整房間。
     * @return 全部已綁定實體可確認時回傳當下描述；無法確認時為 null。
     */
    fun prepareStart(room: Room): List<AiCharacterParticipantCapture>? {
        val current = server ?: return rejectStart(room, "session_unavailable")
        check(current.isOnThread) { "Participant preflight requires the server thread" }
        if (room.admission.memberIdentities.isEmpty()) return emptyList()
        if (persistence.characterSnapshot()?.characters == null) return rejectStart(room, "character_data_unavailable")
        val seating = sessions.prepareTable(room.id) ?: return rejectStart(room, "seating_session_unavailable")
        val observations = linkedMapOf<Uuid, SeatedParticipantObservation>()
        for ((player, identity) in room.admission.memberIdentities) {
            if (identity.sourceId != SeatedParticipantSources.WORLD_ENTITY) return rejectStart(room, "unsupported_source", player)
            val entity = seating.world.getEntity(identity.subjectId.toJavaUuid()) ?: return rejectStart(room, "entity_not_loaded", player)
            val index = room.playerIds.indexOf(player)
            if (index < 0) return rejectStart(room, "member_not_in_roster", player)
            if (!validEntity(entity, seating.world)) return rejectStart(room, "entity_unavailable_or_bound", player)
            if (!entity.hasVehicle()) return rejectStart(room, "entity_not_riding", player)
            val seatStates = seating.requests.map { seating.coordinator.inspectSeat(seating.world, entity, it).state }
            if (SeatedParticipantPresence.resolve(seatStates) != SeatedParticipantPresence.PRESENT) return rejectStart(room, "not_in_table_seat", player)
            val seatState = SeatState.SEATED
            val participant = when (val check = describe(entity, room.hostId)) {
                is ParticipantCheck.Eligible -> check.participant
                ParticipantCheck.Ineligible -> return rejectStart(room, "provider_ineligible", player)
                ParticipantCheck.Unavailable -> return rejectStart(room, "provider_unavailable", player)
            }
            observations[player] = SeatedParticipantObservation(identity, seatState, captureAppearance(entity, participant.character))
        }
        return SeatedParticipantStartPreflight.capture(room, observations) ?: rejectStart(room, "identity_verification_failed")
    }

    /**
     * 只在有人要求開局時記錄拒絕原因，不因定期掃描持續輸出相同警告。
     *
     * @param room 即將開局的房間。
     * @param reason 未通過的檢查。
     * @param player 涉及的原成員編號；整個房間不可用時為 null。
     * @return 開局複核失敗的結果。
     */
    private fun rejectStart(
        room: Room,
        reason: String,
        player: Uuid? = null,
    ): List<AiCharacterParticipantCapture>? {
        logger.warn(
            "Seated participant start rejected tableId={} memberId={} reason={}",
            room.id,
            player,
            reason,
        )
        return null
    }

    /**
     * 只擷取實體的普通自訂名稱；類型名稱由角色翻譯鍵交給客戶端解析。
     *
     * @param entity 已驗證的世界實體。
     * @param character 提供者的公開角色描述。
     * @return 不含綁定身分的公開外觀。
     */
    private fun captureAppearance(entity: Entity, character: AiCharacterDefinition): AiCharacterSnapshot = AiCharacterSnapshot(
        characterId = character.id,
        definition = character,
        displayName = (entity.customName ?: entity.name.takeUnless { it.content is TranslatableTextContent })
            ?.takeUnless { it.content is TranslatableTextContent }?.string?.take(128)?.takeIf(String::isNotBlank),
    )

    /**
     * 依登記順序辨識並驗證實體與房主的工作授權；失敗不嘗試另一個能力掩蓋。
     *
     * @param entity 當下實體。
     * @param hostId 房主。
     * @return 已確認資格、不合格或無法確認三種結果。
     */
    private fun describe(entity: Entity, hostId: Uuid): ParticipantCheck {
        for (definition in participants.definitions()) {
            try {
                val description = definition.provider.describe(entity) ?: continue
                if (!definition.provider.canJoin(entity, hostId)) return ParticipantCheck.Ineligible
                if (characters.find(description.character.id) == null ||
                    capabilities.find(description.character.worldCapabilityId ?: return ParticipantCheck.Ineligible) == null ||
                    description.strategyKey !in strategies.getAllStrategyKeys()
                ) {
                    return ParticipantCheck.Ineligible
                }
                return ParticipantCheck.Eligible(description)
            } catch (cause: Throwable) {
                if (cause is CancellationException || !failurePolicy.isIsolatable(cause)) throw cause
                val signature = "${cause::class.qualifiedName}:${cause.message}"
                if (reported.put(definition.id, signature) != signature) logger.warn("Room participant callback failed providerId={} entityId={}", definition.id, entity.uuid, cause)
                return ParticipantCheck.Unavailable
            }
        }
        return ParticipantCheck.Ineligible
    }

    /**
     * 確認候選是目前世界裡已載入、未被接管的非玩家實體。
     *
     * @param entity 候選。
     * @param world 要求的世界。
     * @return 有效時為 true。
     */
    private fun validEntity(entity: Entity, world: ServerWorld): Boolean = entity !is ServerPlayerEntity &&
        entity.isAlive &&
        entity.world === world &&
        world.getEntity(entity.uuid) === entity &&
        world.chunkManager.isChunkLoaded(entity.blockX shr 4, entity.blockZ shr 4) &&
        bindings.coordinator.keyFor(entity.uuid.toKotlinUuid()) == null

    /**
     * 從同一權威封套檢查來源唯一性。
     *
     * @param identity 經驗證的穩定來源。
     * @return 已在任一房間或對局時為 true。
     */
    private fun isClaimed(identity: RoomParticipantIdentity): Boolean {
        val state = persistence.characterSnapshot() ?: return true
        return identity in state.claimedParticipants
    }

    /** 明確區分不合格與無法確認，提供者失敗不被當作離座。 */
    private sealed interface ParticipantCheck {
        /**
         * 已確認可參加。
         *
         * @property participant 經驗證描述。
         */
        data class Eligible(val participant: FabricRoomParticipant) : ParticipantCheck

        /** 明確不支援或未獲授權。 */
        data object Ineligible : ParticipantCheck

        /** 提供者暫時失敗，不能據此移除已存在成員。 */
        data object Unavailable : ParticipantCheck
    }

    /** 有界掃描與查詢的初始上限。 */
    private companion object {
        /** 每秒補查一次。 */
        const val SCAN_INTERVAL: Int = 20

        /** 一輪只收斂八個房間。 */
        const val ROOMS_PER_SCAN: Int = 8

        /** 每席最多驗證十六個候選，更多時保守略過。 */
        const val CANDIDATES_PER_SEAT: Int = 16

        /** 一次查詢最多讀取的附近桌子。 */
        const val TABLES_PER_QUERY: Int = 32

        /** 一次查詢最多回傳的席位。 */
        const val RESULTS_PER_QUERY: Int = 8

        /** 每隻實體最多每秒查詢一次。 */
        const val QUERY_INTERVAL: Long = 20L

        /** 全服一個 tick 最多執行的查詢數。 */
        const val QUERIES_PER_TICK: Int = 16

        /** 限流記錄容量。 */
        const val MAX_QUERY_SUBJECTS: Int = 4096
    }
}
