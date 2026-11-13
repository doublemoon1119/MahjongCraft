package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterContext
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatingSession
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatingSessionFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.SeatCollisionScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterBindingKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterLease
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterLookFocus
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterLookSchedule
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSeatActivation
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSeatRiders
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.SeatedParticipantSources
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.SeatAssignmentResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableInteractionReach
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatAnchor
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatOffset
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec3d
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 依權威名冊及已載入桌子恢復世界角色，不依賴開局事件補發，也不寫入遊戲狀態。
 *
 * @property persistence 角色固定快照及權威名冊。
 * @property bindings 唯一角色接管紀錄。
 * @property capabilities 已凍結的原生能力。
 * @property sessions 與真人共用的席位入口。
 * @property serverHolder 目前原生 session。
 * @property spawnGateway 自有角色加入世界的唯一入口。
 * @property tableLocations 不載入世界的位置索引。
 * @property failurePolicy 擴充失敗隔離政策。
 * @property tracking 公開角色接管標記。
 */
class FabricAiCharacterManager internal constructor(
    private val persistence: FabricAuthoritativeStatePersistence,
    private val bindings: FabricAiCharacterBindings,
    private val capabilities: FabricAiCharacterCapabilityRegistry,
    private val sessions: FabricSeatingSessionFactory,
    private val serverHolder: FabricServerHolder,
    private val spawnGateway: FabricEntitySpawnGateway,
    private val tableLocations: TableLocationRegistry,
    private val failurePolicy: ExtensionFailurePolicy,
    private val tracking: FabricAiCharacterTracking,
) {
    /** 角色能力及公開回饋的診斷。 */
    private val logger = mahjongCraftLogger(FabricAiCharacterManager::class)

    /** 接受工作的原生 session。 */
    private var activeServer: MinecraftServer? = null

    /** 主執行緒 tick，用於有界重試頻率。 */
    private var ticks = 0L

    /** 無法生成時每隔一段時間才重試，不忙迴圈。 */
    private val retryAfter = mutableMapOf<AiCharacterBindingKey, Long>()

    /** 此 session 已回報的缺少能力。 */
    private val unavailable = mutableSetOf<String>()

    /** 上次已安排角色的完整座位名冊。 */
    private val seatedRosters = mutableMapOf<Uuid, List<Uuid>>()

    /** 剛成立的公開棄牌目標及失效 tick。 */
    private val recentDiscards = mutableMapOf<Uuid, FabricAiCharacterDiscardLook>()

    /** 登記 tick 及卸載回呼；只執行目前 session 的工作。 */
    fun registerEvents() {
        EntityTrackingEvents.START_TRACKING.register { entity, player ->
            if (bindings.findEntity(entity) != null) tracking.send(entity, player, true)
        }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (activeServer === server) {
                ticks++
                if (ticks % RECONCILE_INTERVAL == 0L) reconcile()
            }
        }
        ServerChunkEvents.CHUNK_UNLOAD.register { world, chunk ->
            if (activeServer !== world.server) return@register
            bindings.coordinator.keys().forEach { key ->
                val actor = bindings.coordinator.find(key) ?: return@forEach
                val location = tableLocations.get(key.tableId)?.location
                val tableInChunk = location?.dimensionId == world.registryKey.value.toString() &&
                    location.x shr CHUNK_SHIFT == chunk.pos.x &&
                    location.z shr CHUNK_SHIFT == chunk.pos.z
                if (actor.entity.world === world && (actor.entity.chunkPos == chunk.pos || tableInChunk)) bindings.coordinator.remove(key)
            }
        }
    }

    /**
     * 開始本次原生 session；由之後的收斂恢復已載入桌子，不補發遊戲事件。
     *
     * @param server 已完成資料與世界索引還原的伺服器。
     */
    fun startSession(server: MinecraftServer) {
        stopSession()
        activeServer = server
        bindings.startSession()
    }

    /** 停止新工作，釋放借用角色及自有角色。 */
    fun stopSession() {
        activeServer = null
        bindings.stopSession()
        retryAfter.clear()
        unavailable.clear()
        seatedRosters.clear()
        recentDiscards.clear()
        ticks = 0L
    }

    /**
     * 開局或換位呈現前立即對齊名冊；自有代理共用入座，借用角色只接收指定席位，不自動拉回。
     *
     * @param tableId 只對齊指定桌子；null 表示所有權威桌子。
     */
    fun reconcile(tableId: Uuid? = null) {
        val server = activeServer ?: return
        if (serverHolder.current() !== server) return
        check(server.isOnThread) { "AI characters require the server thread" }
        val snapshot = persistence.characterSnapshot()
        val games = snapshot?.authoritative?.games.orEmpty()
        val metadata = snapshot?.characters?.matches.orEmpty()
        val expected = games.values.flatMap { game -> game.aiPlayerIds.map { AiCharacterBindingKey(game.id, game.matchId, it) } }.toSet()
        bindings.retain(expected)
        retryAfter.keys.retainAll(expected)
        seatedRosters.keys.retainAll(games.keys)
        recentDiscards.keys.retainAll(games.keys)
        games.values.filter { tableId == null || it.id == tableId }.forEach { game ->
            if (activeServer !== server) return
            reconcileGame(game, metadata[game.matchId].orEmpty())
        }
    }

    /**
     * 對齊單場代理，使用完整名冊建立席位。
     *
     * @param game 本次讀取的權威對局。
     * @param descriptions 本場固定的公開角色描述。
     */
    private fun reconcileGame(game: Game, descriptions: Map<Uuid, AiCharacterSnapshot>) {
        val roster = game.tableState.players.map { it.id }
        val seating = sessions.prepare(game.id, roster)
        if (seating == null) {
            game.aiPlayerIds.forEach { bindings.coordinator.remove(AiCharacterBindingKey(game.id, game.matchId, it)) }
            seatedRosters.remove(game.id)
            return
        }
        var changed = seatedRosters[game.id] != roster
        game.aiPlayerIds.forEach { playerId ->
            val key = AiCharacterBindingKey(game.id, game.matchId, playerId)
            val current = bindings.coordinator.find(key)
            if (current != null) {
                if (game.roomAdmission.memberIdentities[playerId]?.sourceId == SeatedParticipantSources.WORLD_ENTITY && !isNearby(seating, current.entity)) {
                    bindings.coordinator.remove(key)
                    return@forEach
                }
                if (current.handle.ownership == AiCharacterOwnership.OWNED && current.expectedPosition?.squaredDistanceTo(current.entity.pos)?.let { it > POSITION_TOLERANCE_SQUARED } == true) changed = true
                return@forEach
            }
            if (ticks < retryAfter.getOrElse(key) { 0L }) return@forEach
            retryAfter[key] = ticks + RETRY_INTERVAL
            val definition = descriptions[playerId]?.definition ?: return@forEach
            val index = roster.indexOf(playerId)
            if (index >= 0 && acquire(key, definition, seating, index) != null) changed = true
        }
        if (changed) reseatActors(game, roster)
        updateLook(game, seating, roster)
    }

    /**
     * 以一般桌邊互動範圍區分附近離座與已離場，不要求站在精確席位上。
     *
     * @param seating 已載入桌子的世界與位置。
     * @param entity 原世界參與者。
     * @return 原實體仍在同一桌邊範圍內時為 true。
     */
    private fun isNearby(seating: FabricSeatingSession, entity: Entity): Boolean = entity.world === seating.world &&
        TableInteractionReach.distance(
            entity.x,
            entity.y,
            entity.z,
            seating.location.x,
            seating.location.y,
            seating.location.z,
        ) <= TableInteractionReach.SURVIVAL

    /**
     * 查詢能力並透過唯一接管核心建立角色。
     *
     * @param key 本場代理身分。
     * @param definition 本場固定角色描述。
     * @param seating 已確認載入的標準席位。
     * @param index AI 的完整席位索引。
     * @return 成功啟用的角色，否則為 null。
     */
    private fun acquire(
        key: AiCharacterBindingKey,
        definition: AiCharacterDefinition,
        seating: FabricSeatingSession,
        index: Int,
    ): FabricAiCharacterActor? {
        val capabilityId = definition.worldCapabilityId ?: return null
        val provider = capabilities.find(capabilityId)?.provider
        if (provider == null) {
            if (unavailable.add(capabilityId)) logger.warn("AI character capability is unavailable capabilityId={}", capabilityId)
            return null
        }
        val game = persistence.characterSnapshot()?.authoritative?.games?.get(key.tableId) ?: return null
        val identity = game.roomAdmission.memberIdentities[key.playerId]
        val participant = if (identity?.sourceId == SeatedParticipantSources.WORLD_ENTITY) {
            seating.world.getEntity(identity.subjectId.toJavaUuid())?.takeIf { it.isAlive && isNearby(seating, it) } ?: return null
        } else {
            null
        }
        val context = FabricAiCharacterContext.create(
            world = seating.world,
            tableId = key.tableId,
            matchId = key.matchId,
            playerId = key.playerId,
            character = definition,
            seat = seating.requests[index],
            participant = participant,
        )
        return bindings.coordinator.ensure(
            key = key,
            acquire = {
                val handle = provider.acquire(context) ?: return@ensure null
                val entity = handle.entity
                check(entity !is ServerPlayerEntity && entity.isAlive && entity.world === seating.world) { "Character must be a non-player entity in the requested world: $capabilityId" }
                val present = seating.world.getEntity(entity.uuid)
                val ownership = handle.ownership
                check(participant == null || (entity === participant && ownership == AiCharacterOwnership.BORROWED)) { "Room participant must borrow its exact bound entity" }
                check(ownership != AiCharacterOwnership.OWNED || !entity.type.isSaveable) { "Owned character entity type must disable saving: $capabilityId" }
                val registered = if (ownership == AiCharacterOwnership.OWNED) {
                    present == null
                } else {
                    present === entity &&
                        seating.world.chunkManager.isChunkLoaded(entity.blockPos.x shr CHUNK_SHIFT, entity.blockPos.z shr CHUNK_SHIFT)
                }
                check(registered) { "Character ownership does not match world registration: $capabilityId" }
                FabricAiCharacterLease(
                    rider = FabricAiCharacterActor(
                        entity = entity,
                        handle = handle,
                        capabilityId = capabilityId,
                    ),
                    world = seating.world,
                    ownership = ownership,
                    tracking = tracking,
                    preserveSeatOnRelease = participant != null,
                )
            },
            activate = { lease ->
                val current = persistence.characterSnapshot()?.authoritative?.games?.get(key.tableId)
                current?.matchId == key.matchId && key.playerId in current.aiPlayerIds && activate(lease, seating, index, key.tableId)
            },
        ).also { actor ->
            if (actor != null) tracking.broadcast(actor.entity, true)
        }
    }

    /**
     * 在已成功放置座位後只安排指定角色，不移動其他玩家或 AI。
     *
     * @param key 發起互動時的接管身分。
     * @param actor 當時的原生角色。
     * @return 此角色已在同一場次入座時為 true。
     */
    internal fun seatAfterPlacement(key: AiCharacterBindingKey, actor: FabricAiCharacterActor): Boolean {
        if (activeServer == null || bindings.find(key.tableId, key.playerId) !== actor || actor.entity.hasVehicle()) return false
        val game = persistence.characterSnapshot()?.authoritative?.games?.get(key.tableId) ?: return false
        if (game.matchId != key.matchId) return false
        val roster = game.tableState.players.map { it.id }
        val index = roster.indexOf(key.playerId)
        if (index < 0) return false
        val seating = sessions.prepare(key.tableId, roster) ?: return false
        val actors = roster.map { if (it == key.playerId) actor.entity else null }
        val result = seating.coordinator.seatAll(seating.world, actors, seating.requests)[index]
        actor.assignedVehicle = actor.entity.vehicle
        actor.expectedPosition = actor.entity.pos
        if (result != SeatAssignmentResult.SEATED) return false
        callback(actor, SEAT_CALLBACK) { actor.handle.seatAssigned(seating.requests[index]) }
        return true
    }

    /**
     * 局間換位或恢復時只安排自有代理；借用角色接收新席位後自行處理，真人位置不變。
     *
     * @param game 目前權威對局。
     * @param roster 完整席位名冊。
     */
    private fun reseatActors(game: Game, roster: List<Uuid>) {
        val seating = sessions.prepare(game.id, roster) ?: return
        val actors = AiCharacterSeatRiders.resolve<Entity>(
            roster = roster,
            includePlayers = false,
            player = serverHolder::findPlayer,
            character = { playerId ->
                bindings.find(game.id, playerId)?.takeIf { it.handle.ownership == AiCharacterOwnership.OWNED }?.entity
            },
        )
        val tableRequests = sessions.prepareTable(game.id)?.requests ?: return
        SeatCollisionScope.with(actors, seating.requests) {
            seating.coordinator.seatAll(seating.world, actors, seating.requests, tableRequests)
        }.forEachIndexed { index, result ->
            val current = bindings.find(game.id, roster[index])
            if (current?.handle?.ownership == AiCharacterOwnership.BORROWED) {
                callback(current, SEAT_CALLBACK) { current.handle.seatAssigned(seating.requests[index]) }
                return@forEachIndexed
            }
            current?.let { it.assignedVehicle = actors[index]?.vehicle }
            if (result == SeatAssignmentResult.SEATED || result == SeatAssignmentResult.STANDING) {
                bindings.find(game.id, roster[index])?.let { actor ->
                    callback(actor, SEAT_CALLBACK) { actor.handle.seatAssigned(seating.requests[index]) }
                    callback(actor, LOOK_CALLBACK) { actor.handle.lookAt(null) }
                    actor.lookTarget = null
                    actor.expectedPosition = actor.entity.pos
                }
            } else if (actors[index] != null) {
                val key = AiCharacterBindingKey(game.id, game.matchId, roster[index])
                bindings.coordinator.remove(key)
                retryAfter[key] = ticks + RETRY_INTERVAL
            }
        }
        seatedRosters[game.id] = roster
    }

    /**
     * 主要看各自手牌實際的位置，錯開短暫轉看公開捨牌及目前行動者的時機。
     *
     * @param game 目前權威對局；只用手牌的牌張身分找出桌上的位置，不讀取牌面或待決選擇。
     * @param seating 已載入的席位世界。
     * @param roster 完整席位名冊。
     */
    private fun updateLook(
        game: Game,
        seating: FabricSeatingSession,
        roster: List<Uuid>,
    ) {
        val recent = recentDiscards[game.id]
        val discardTarget = recent?.takeIf {
            it.isCurrent(ticks, game.tableState.players.flatMap { player -> player.discardPile.entries.filterNot { entry -> entry.isTaken }.map { entry -> entry.tile.id } }.toSet())
        }?.position
        if (discardTarget == null) recentDiscards.remove(game.id)
        val participantTarget = game.tableState.currentPlayer.id.let { current ->
            if (current in game.aiPlayerIds) bindings.find(game.id, current)?.entity else serverHolder.findPlayer(current)
        }?.takeIf { it.world === seating.world && !it.isRemoved && seating.world.chunkManager.isChunkLoaded(it.blockPos.x shr CHUNK_SHIFT, it.blockPos.z shr CHUNK_SHIFT) }?.eyePos
        roster.forEachIndexed { index, playerId ->
            val actor = bindings.find(game.id, playerId) ?: return@forEachIndexed
            val placement = MahjongTileTableLayout.seatAnchorPlacement(
                controllerX = seating.location.x,
                controllerY = seating.location.y,
                controllerZ = seating.location.z,
                tableFacing = seating.facing,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(roster.size),
                seatIndex = index,
                anchor = TableSeatAnchor.HAND_ROW_CENTER,
                offset = TableSeatOffset(),
            )
            // 看向自己手牌實際的位置，手牌隨規則的桌面配置移動時視線跟著移動；手牌還沒擺上桌時看手牌那一列的中央。
            val handTiles = game.tableState.players.firstOrNull { it.id == playerId }?.hand?.let { hand -> hand.tiles + listOfNotNull(hand.lastDrawn) }.orEmpty()
                .mapNotNull { tile -> seating.world.getEntity(tile.id.toJavaUuid())?.takeUnless { it.isRemoved }?.pos }
            val handTarget = if (handTiles.isEmpty()) {
                Vec3d(placement.x, placement.y, placement.z)
            } else {
                Vec3d(handTiles.sumOf { it.x } / handTiles.size, handTiles.sumOf { it.y } / handTiles.size, handTiles.sumOf { it.z } / handTiles.size)
            }
            val requested = when (AiCharacterLookSchedule.focus(playerId, ticks)) {
                AiCharacterLookFocus.HAND_AREA -> handTarget
                AiCharacterLookFocus.DISCARD -> discardTarget ?: handTarget
                AiCharacterLookFocus.PARTICIPANT -> participantTarget?.takeIf { game.tableState.currentPlayer.id != playerId } ?: handTarget
            }
            val chosen = requested.takeIf { actor.entity.pos.squaredDistanceTo(it) <= MAX_LOOK_DISTANCE_SQUARED && actor.entity.eyePos.squaredDistanceTo(it) > SELF_LOOK_TOLERANCE }
            if (chosen != actor.lookTarget) {
                actor.lookTarget = chosen
                callback(actor, LOOK_CALLBACK) { actor.handle.lookAt(chosen) }
            }
        }
    }

    /**
     * 讓自有角色先取得安全位置才加入世界，再由原有協調器執行入座。
     *
     * @param lease 已接管的角色。
     * @param seating 本次標準席位。
     * @param index 此 AI 的完整名冊索引。
     * @param tableId 診斷桌子。
     * @return 自有代理已完成安全入座，或借用角色已收到指定席位時為 true。
     */
    private fun activate(
        lease: AiCharacterLease<FabricAiCharacterActor>,
        seating: FabricSeatingSession,
        index: Int,
        tableId: Uuid,
    ): Boolean {
        val actor = lease.rider
        val request = seating.requests[index]
        val activated = AiCharacterSeatActivation.activate(
            ownership = lease.ownership,
            preview = {
                seating.coordinator.preview(seating.world, actor.entity, request).also { target ->
                    if (target == null) {
                        logger.debug("AI character activation deferred before spawn tableId={} seatIndex={} entityId={}", tableId, index, actor.entity.uuid)
                        FabricAiCharacterActivationDiagnostics.beforeSpawn(
                            seating.world,
                            actor.entity,
                            request,
                            index,
                        )
                    }
                }
            },
            position = { geometry -> actor.entity.refreshPositionAndAngles(geometry.x, geometry.y, geometry.z, geometry.yaw, 0f) },
            spawn = { spawnGateway.spawn(seating.world, actor.entity, SPAWN_SOURCE, tableId) },
            seat = {
                val result = seating.coordinator.assign(seating.world, actor.entity, request)
                actor.assignedVehicle = actor.entity.vehicle
                if (result != SeatAssignmentResult.SEATED && result != SeatAssignmentResult.STANDING) {
                    logger.debug("AI character activation deferred after spawn tableId={} seatIndex={} entityId={} result={}", tableId, index, actor.entity.uuid, result)
                }
                result == SeatAssignmentResult.SEATED || result == SeatAssignmentResult.STANDING
            },
        )
        if (!activated) return false
        actor.assignedVehicle = actor.entity.vehicle
        actor.handle.seatAssigned(request)
        actor.expectedPosition = actor.entity.pos
        return true
    }

    /**
     * 一次已成立捨牌的回饋；目標必須來自牌河配置，不能使用飛行前的私人手牌位置。
     *
     * @param tableId 桌子 UUID。
     * @param seatIndex 已成立動作的公開席位。
     * @param tileId 已成立的捨牌 UUID。
     * @param target 已公開的牌河目標位置。
     */
    fun onDiscard(
        tableId: Uuid,
        seatIndex: Int,
        tileId: Uuid,
        target: Vec3d,
    ) {
        if (activeServer == null) return
        val game = persistence.characterSnapshot()?.authoritative?.games?.get(tableId) ?: return
        val playerId = game.tableState.players.getOrNull(seatIndex)?.id ?: return
        recentDiscards[tableId] = FabricAiCharacterDiscardLook(tileId, target, ticks + DISCARD_LOOK_TICKS)
        bindings.find(tableId, playerId)?.let { actor -> callback(actor, ANIMATE_CALLBACK) { actor.handle.animate(AiCharacterActionIds.DISCARD) } }
    }

    /**
     * 每份能力的回饋只報錯一次，不讓角色動畫中斷牌局。
     *
     * @param actor 來源角色。
     * @param kind 注視或動作。
     * @param block 外部回呼。
     */
    private fun callback(actor: FabricAiCharacterActor, kind: String, block: () -> Unit) {
        if (kind in actor.failedCallbacks) return
        try {
            block()
        } catch (cause: Throwable) {
            if (cause is CancellationException || !failurePolicy.isIsolatable(cause)) throw cause
            actor.failedCallbacks += kind
            logger.warn("AI character callback failed capabilityId={} entityId={} callback={}", actor.capabilityId, actor.entity.uuid, kind, cause)
        }
    }

    /** 主執行緒的角色收斂及回饋常數。 */
    private companion object {
        /** 注視回饋的診斷種類。 */
        const val LOOK_CALLBACK: String = "look"

        /** 動作回饋的診斷種類。 */
        const val ANIMATE_CALLBACK: String = "animate"

        /** 入座回饋的診斷種類。 */
        const val SEAT_CALLBACK: String = "seat"

        /** 已安排腳底位置的容許誤差平方。 */
        const val POSITION_TOLERANCE_SQUARED: Double = 0.01

        /** 每五 tick 檢查公開目標及名冊。 */
        const val RECONCILE_INTERVAL: Long = 5L

        /** 無法啟用的角色每兩秒重試。 */
        const val RETRY_INTERVAL: Long = 40L

        /** 剛捨牌的短暫注視時間。 */
        const val DISCARD_LOOK_TICKS: Long = 16L

        /** 不追蹤遠離桌子的玩家。 */
        const val MAX_LOOK_DISTANCE_SQUARED: Double = 64.0

        /** 不注視自己的眼睛。 */
        const val SELF_LOOK_TOLERANCE: Double = 0.01

        /** 原生區塊座標位移。 */
        const val CHUNK_SHIFT: Int = 4

        /** 生成入口的診斷來源。 */
        const val SPAWN_SOURCE: String = "ai_character"
    }
}
