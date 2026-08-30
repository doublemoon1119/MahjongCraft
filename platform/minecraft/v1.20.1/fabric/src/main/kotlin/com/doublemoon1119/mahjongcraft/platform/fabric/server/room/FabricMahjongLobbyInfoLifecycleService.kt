package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.runBlocking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import org.koin.core.annotation.Single

/** 每秒以權威 Room 狀態修復或清除提示；正常內容更新仍由 Room event 即時觸發。 */
@Single
class FabricMahjongLobbyInfoLifecycleService(
    private val rooms: RoomRepository,
    private val locations: TableLocationRegistry,
    private val presenter: FabricMahjongLobbyInfoPresenter,
) {
    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(::onEndServerTick)
    }

    private fun onEndServerTick(server: MinecraftServer) {
        if (server.ticks % REFRESH_INTERVAL_TICKS != 0) return
        runBlocking {
            locations.snapshot().forEach { (tableId, entry) ->
                val identifier = Identifier.tryParse(entry.location.dimensionId) ?: return@forEach
                val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, identifier)) ?: return@forEach
                if (!world.chunkManager.isChunkLoaded(entry.location.chunkX, entry.location.chunkZ)) return@forEach
                val room = rooms.getRoom(tableId)
                if (room == null) {
                    presenter.clear(tableId)
                } else {
                    presenter.present(tableId, room.gameConfig, room.playerIds.size)
                }
            }
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_TICKS = 20
    }
}
