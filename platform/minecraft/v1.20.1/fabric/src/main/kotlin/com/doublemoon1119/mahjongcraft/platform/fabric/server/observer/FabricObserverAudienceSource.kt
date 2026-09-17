package com.doublemoon1119.mahjongcraft.platform.fabric.server.observer

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverAudienceSource
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.withContext
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 以 Minecraft 原版的追蹤範圍決定誰是觀察者：客戶端已載入麻將桌所在區塊的玩家即為該桌房間或對局的
 * 觀察者。
 *
 * 不維護任何名單——玩家走遠、離線或區塊卸載後自然不再出現在查詢結果中，玩家重新靠近時也不需要任何
 * 登記動作。[TableLocationRegistry] 與世界狀態都只能在伺服器主執行緒存取，因此整趟查詢在
 * [CoroutineDispatchers.main] 上完成，一次取回所有結果。
 */
@Single(binds = [ObserverAudienceSource::class])
class FabricObserverAudienceSource(
    private val locations: TableLocationRegistry,
    private val serverHolder: FabricServerHolder,
    private val dispatchers: CoroutineDispatchers,
) : ObserverAudienceSource {
    override suspend fun observersById(): Map<Uuid, Set<Uuid>> = withContext(dispatchers.main) {
        val server = serverHolder.current() ?: return@withContext emptyMap()
        buildMap {
            locations.snapshot().forEach { (id, entry) ->
                val dimension = Identifier.tryParse(entry.location.dimensionId) ?: return@forEach
                val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimension)) ?: return@forEach
                val position = BlockPos(entry.location.x, entry.location.y, entry.location.z)
                val observerIds = PlayerLookup.tracking(world, position)
                    .mapTo(mutableSetOf()) { player -> player.uuid.toKotlinUuid() }
                if (observerIds.isNotEmpty()) put(id, observerIds)
            }
        }
    }
}
