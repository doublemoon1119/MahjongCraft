package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.DimensionChunkKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.runBlocking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

/** 在 tick 邊界後登記已載入桌子，並定點驗證已載入 chunk 的預期位置。 */
@Single
class FabricTableLocationValidationService(
    private val locations: TableLocationRegistry,
    private val cleanupService: OrphanedTableCleanupService,
) {
    /** 回報相同 UUID 移動與位置驗證結果。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 目前允許處理延遲工作的 server session。 */
    private var activeServer: MinecraftServer? = null

    /** 管理 chunk 載入後的延遲缺失確認。 */
    private val validationQueue = TableLocationValidationQueue<MinecraftServer, ServerWorld>()

    /** 下一個 tick 邊界才可處理的 BlockEntity 載入工作。 */
    private var nextTableLoads = mutableListOf<PendingTableLoad>()

    /** 本次 tick 邊界要處理的 BlockEntity 載入工作。 */
    private var readyTableLoads = mutableListOf<PendingTableLoad>()

    /** 註冊 Fabric BlockEntity、chunk 與 server tick 事件。 */
    fun registerEvents() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register { blockEntity, world ->
            if (blockEntity is MahjongTableBlockEntity) nextTableLoads.add(PendingTableLoad(world, blockEntity.pos))
        }
        ServerChunkEvents.CHUNK_LOAD.register { world, chunk ->
            validationQueue.enqueueChunk(world, chunk.pos.x, chunk.pos.z)
        }
        ServerTickEvents.END_SERVER_TICK.register(::onEndServerTick)
    }

    /** 啟用指定 server session 並保留啟動期間已排入的載入事件。 */
    fun startSession(server: MinecraftServer) {
        activeServer = server
        validationQueue.startSession(server)
        logger.debug("Started Mahjong table location validation for the current server session")
    }

    /** 停止處理並清除所有屬於舊 session 的延遲工作。 */
    fun stopSession() {
        val pendingRequestCount = nextTableLoads.size + readyTableLoads.size + validationQueue.pendingCount
        activeServer = null
        nextTableLoads.clear()
        readyTableLoads.clear()
        validationQueue.stopSession()
        logger.debug("Stopped Mahjong table location validation and cleared {} pending request(s)", pendingRequestCount)
    }

    /** 在明確 tick 邊界處理上一輪工作，再把本輪事件移到下一輪。 */
    private fun onEndServerTick(server: MinecraftServer) {
        if (activeServer !== server) return

        readyTableLoads.forEach(::processTableLoad)
        runBlocking {
            validationQueue.advance(
                session = server,
                isChunkUsable = ::isUsable,
                entriesForChunk = ::entriesForChunk,
                isEntryCurrent = { entry -> locations.get(entry.tableId) == entry },
                matchesExpectedTable = ::matchesExpectedTable,
                cleanup = ::cleanupMissing,
            )
        }

        readyTableLoads = nextTableLoads.also { nextTableLoads = mutableListOf() }
    }

    /** 在 BlockEntity NBT 完成載入後登記目前 UUID 與位置。 */
    private fun processTableLoad(request: PendingTableLoad) {
        if (!isUsable(request.world, request.pos.x shr 4, request.pos.z shr 4)) return
        val table = request.world.getBlockEntity(request.pos) as? MahjongTableBlockEntity ?: return
        val location = request.world.toTableLocation(request.pos)
        val previous = locations.get(table.tableId)
        val current = locations.put(table.tableId, location)
        if (previous != null && previous.location != current.location) {
            logger.warn(
                "Updated Mahjong table {} location from {} to {}",
                table.tableId,
                previous.location,
                current.location,
            )
        }
    }

    /** 取得指定 chunk 的目前位置索引。 */
    private fun entriesForChunk(world: ServerWorld, chunkX: Int, chunkZ: Int): Collection<TableLocationEntry> {
        val key = DimensionChunkKey(world.registryKey.value.toString(), chunkX, chunkZ)
        return locations.getByChunk(key)
    }

    /** 第二次確認仍缺失後套用 orphan cleanup。 */
    private suspend fun cleanupMissing(entry: TableLocationEntry) {
        logger.debug(
            "Confirmed missing Mahjong table {} at {}; applying orphan cleanup",
            entry.tableId,
            entry.location,
        )
        cleanupService.cleanupMissing(entry.tableId, entry.revision)
    }

    /** 確認 request 仍屬於目前 session 且 chunk 仍已載入。 */
    private fun isUsable(world: ServerWorld, chunkX: Int, chunkZ: Int): Boolean = world.server === activeServer &&
        world.chunkManager.isChunkLoaded(chunkX, chunkZ)

    /** 定點確認 BlockEntity 類型與 UUID 都符合索引。 */
    private fun matchesExpectedTable(world: ServerWorld, entry: TableLocationEntry): Boolean {
        val location: TableLocation = entry.location
        val table = world.getBlockEntity(BlockPos(location.x, location.y, location.z)) as? MahjongTableBlockEntity
        if (table?.tableId != entry.tableId) return false
        val block = table.cachedState.block as? MahjongTableBlock ?: return false
        val complete = block.isComplete(world, table.pos, table.cachedState)
        if (!complete) logger.warn("Found incomplete Mahjong table {} at {}", table.tableId, entry.location)
        return complete
    }

    /** 尚待 BlockEntity NBT 完成載入的位置。 */
    private data class PendingTableLoad(
        /** BlockEntity 所在世界。 */
        val world: ServerWorld,
        /** BlockEntity 所在座標。 */
        val pos: BlockPos,
    )
}
