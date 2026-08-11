package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

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

    /** 下一個 tick 邊界才可處理的 BlockEntity 載入工作。 */
    private var nextTableLoads = mutableListOf<PendingTableLoad>()

    /** 本次 tick 邊界要處理的 BlockEntity 載入工作。 */
    private var readyTableLoads = mutableListOf<PendingTableLoad>()

    /** 下一個 tick 邊界才可處理的 chunk 載入工作。 */
    private var nextChunkLoads = mutableListOf<PendingChunkLoad>()

    /** 本次 tick 邊界要處理的 chunk 載入工作。 */
    private var readyChunkLoads = mutableListOf<PendingChunkLoad>()

    /** 下一個 tick 邊界再次確認的缺失位置。 */
    private var nextMissingConfirmations = mutableListOf<PendingMissingConfirmation>()

    /** 本次 tick 邊界要再次確認的缺失位置。 */
    private var readyMissingConfirmations = mutableListOf<PendingMissingConfirmation>()

    /** 註冊 Fabric BlockEntity、chunk 與 server tick 事件。 */
    fun registerEvents() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register { blockEntity, world ->
            if (blockEntity is MahjongTableBlockEntity) nextTableLoads.add(PendingTableLoad(world, blockEntity.pos))
        }
        ServerChunkEvents.CHUNK_LOAD.register { world, chunk ->
            nextChunkLoads.add(PendingChunkLoad(world, chunk.pos.x, chunk.pos.z))
        }
        ServerTickEvents.END_SERVER_TICK.register(::onEndServerTick)
    }

    /** 啟用指定 server session 並保留啟動期間已排入的載入事件。 */
    fun startSession(server: MinecraftServer) {
        activeServer = server
        logger.debug("Started Mahjong table location validation for the current server session")
    }

    /** 停止處理並清除所有屬於舊 session 的延遲工作。 */
    fun stopSession() {
        val pendingRequestCount = nextTableLoads.size + readyTableLoads.size +
            nextChunkLoads.size + readyChunkLoads.size +
            nextMissingConfirmations.size + readyMissingConfirmations.size
        activeServer = null
        nextTableLoads.clear()
        readyTableLoads.clear()
        nextChunkLoads.clear()
        readyChunkLoads.clear()
        nextMissingConfirmations.clear()
        readyMissingConfirmations.clear()
        logger.debug("Stopped Mahjong table location validation and cleared {} pending request(s)", pendingRequestCount)
    }

    /** 在明確 tick 邊界處理上一輪工作，再把本輪事件移到下一輪。 */
    private fun onEndServerTick(server: MinecraftServer) {
        if (activeServer !== server) return

        readyTableLoads.forEach(::processTableLoad)
        readyChunkLoads.forEach(::processChunkLoad)
        readyMissingConfirmations.forEach(::processMissingConfirmation)

        readyTableLoads = nextTableLoads.also { nextTableLoads = mutableListOf() }
        readyChunkLoads = nextChunkLoads.also { nextChunkLoads = mutableListOf() }
        readyMissingConfirmations = nextMissingConfirmations.also { nextMissingConfirmations = mutableListOf() }
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

    /** 取得該 chunk 的預期位置並進行第一次定點檢查。 */
    private fun processChunkLoad(request: PendingChunkLoad) {
        if (!isUsable(request.world, request.chunkX, request.chunkZ)) return
        val key = DimensionChunkKey(request.world.registryKey.value.toString(), request.chunkX, request.chunkZ)
        locations.getByChunk(key).forEach { entry ->
            if (!matchesExpectedTable(request.world, entry)) {
                nextMissingConfirmations.add(PendingMissingConfirmation(request.world, entry))
            }
        }
    }

    /** 第二次確認請求仍有效且桌子仍缺失後，才執行 orphan cleanup。 */
    private fun processMissingConfirmation(request: PendingMissingConfirmation) {
        val location = request.entry.location
        if (!isUsable(request.world, location.chunkX, location.chunkZ)) return
        if (locations.get(request.entry.tableId) != request.entry) return
        if (matchesExpectedTable(request.world, request.entry)) return
        logger.debug(
            "Confirmed missing Mahjong table {} at {}; applying orphan cleanup",
            request.entry.tableId,
            request.entry.location,
        )
        runBlocking { cleanupService.cleanupMissing(request.entry.tableId, request.entry.revision) }
    }

    /** 確認 request 仍屬於目前 session 且 chunk 仍已載入。 */
    private fun isUsable(world: ServerWorld, chunkX: Int, chunkZ: Int): Boolean = world.server === activeServer &&
        world.chunkManager.isChunkLoaded(chunkX, chunkZ)

    /** 定點確認 BlockEntity 類型與 UUID 都符合索引。 */
    private fun matchesExpectedTable(world: ServerWorld, entry: TableLocationEntry): Boolean {
        val location: TableLocation = entry.location
        val table = world.getBlockEntity(BlockPos(location.x, location.y, location.z)) as? MahjongTableBlockEntity
        return table?.tableId == entry.tableId
    }

    /** 尚待 BlockEntity NBT 完成載入的位置。 */
    private data class PendingTableLoad(
        /** BlockEntity 所在世界。 */
        val world: ServerWorld,
        /** BlockEntity 所在座標。 */
        val pos: BlockPos,
    )

    /** 尚待查詢預期位置的已載入 chunk。 */
    private data class PendingChunkLoad(
        /** Chunk 所在世界。 */
        val world: ServerWorld,
        /** Chunk X 座標。 */
        val chunkX: Int,
        /** Chunk Z 座標。 */
        val chunkZ: Int,
    )

    /** 第一次檢查缺失、等待下一個 tick 再確認的位置。 */
    private data class PendingMissingConfirmation(
        /** 預期位置所在世界。 */
        val world: ServerWorld,
        /** 排程時的位置與 revision。 */
        val entry: TableLocationEntry,
    )
}
