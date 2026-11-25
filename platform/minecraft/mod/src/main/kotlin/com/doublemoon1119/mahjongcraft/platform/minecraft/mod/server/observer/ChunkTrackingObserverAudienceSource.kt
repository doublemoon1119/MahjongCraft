package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.observer

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverAudienceSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.ServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.TrackingPlayers
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 以 Minecraft 原版的追蹤範圍決定誰是觀察者：客戶端已載入麻將桌所在區塊的玩家即為該桌房間或對局的
 * 觀察者。
 *
 * 不維護任何名單——玩家走遠、離線或區塊卸載後自然不再出現在查詢結果中，玩家重新靠近時也不需要任何
 * 登記動作。[TableLocationRegistry] 與世界狀態都只能在伺服器主執行緒存取，因此整趟查詢在
 * [CoroutineDispatchers.main] 上完成，一次取回所有結果。
 *
 * @property locations 麻將桌位置索引。
 * @property serverHolder 取得目前伺服器與世界。
 * @property dispatchers 提供伺服器主執行緒。
 */
class ChunkTrackingObserverAudienceSource(
    private val locations: TableLocationRegistry,
    private val serverHolder: ServerHolder,
    private val dispatchers: CoroutineDispatchers,
) : ObserverAudienceSource {
    override suspend fun observersById(): Map<Uuid, Set<Uuid>> = withContext(dispatchers.main) {
        val server = serverHolder.current() ?: return@withContext emptyMap()
        buildMap {
            locations.snapshot().forEach { (id, entry) ->
                val dimension = ResourceLocation.tryParse(entry.location.dimensionId) ?: return@forEach
                val world = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)) ?: return@forEach
                val position = BlockPos(entry.location.x, entry.location.y, entry.location.z)
                val observerIds = TrackingPlayers.of(world, position)
                    .mapTo(mutableSetOf()) { player -> player.uuid.toKotlinUuid() }
                if (observerIds.isNotEmpty()) put(id, observerIds)
            }
        }
    }
}
