package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSpaceCellDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSpaceObstructionDelivery
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSpaceObstructionPayloadDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSpaceObstructionSync
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import kotlin.uuid.toJavaUuid

/**
 * 定期檢查等待開局的桌子擺牌層是否被擋住，並把被擋住的格子同步給該房間在線的真人成員。
 *
 * 只檢查有房間且尚未開局的桌子；沒有房間的桌子可以自由擺放裝飾。送給誰、何時清除由 [TableSpaceObstructionSync] 決定。
 *
 * @property store 房間與對局的權威狀態。
 * @property locations 麻將桌位置索引。
 * @property json 封包序列化設定。
 */
class FabricTableSpaceObstructionService(
    private val store: AuthoritativeStateStore,
    private val locations: TableLocationRegistry,
    private val json: NetworkJson,
) {
    /** 目前啟用的伺服器；session 外為 null。 */
    private var activeServer: MinecraftServer? = null

    /** 啟用 session 後經過的 tick 數。 */
    private var elapsedTicks: Long = 0L

    /** 已送出的標示與收件玩家。 */
    private val sync = TableSpaceObstructionSync()

    /** 註冊伺服器 tick 事件。 */
    fun registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (activeServer !== server) return@register
            elapsedTicks += 1
            if (elapsedTicks % CHECK_INTERVAL_TICKS == 0L) refresh(server)
        }
    }

    /**
     * 啟用指定伺服器的檢查。
     *
     * @param server 目前的伺服器。
     */
    fun startSession(server: MinecraftServer) {
        activeServer = server
        elapsedTicks = 0L
        sync.reset()
    }

    /** 停止檢查並忘記已送出的標示；玩家斷線時客戶端自行清除。 */
    fun stopSession() {
        activeServer = null
        sync.reset()
    }

    /**
     * 檢查所有等待開局的桌子並送出有變化的標示；已不在等待中的桌子送出清除。
     *
     * @param server 目前的伺服器。
     */
    private fun refresh(server: MinecraftServer) {
        val state = store.state.value
        val waitingRooms = state.rooms.filterKeys { tableId -> tableId !in state.games }
        deliver(server, sync.retain(waitingRooms.keys))
        waitingRooms.forEach { (tableId, room) ->
            val location = locations.get(tableId)?.location
            val world = location?.let { resolveWorld(server, it) }
            if (location == null || world == null) {
                deliver(server, sync.clear(tableId))
                return@forEach
            }
            val cells = TableSpaceObstructionScanner.obstructedCells(world, BlockPos(location.x, location.y, location.z))
                ?: return@forEach
            val payload = TableSpaceObstructionPayloadDto(
                tableId = tableId.toString(),
                dimensionId = location.dimensionId,
                cells = cells.map { TableSpaceCellDto(it.x, it.y, it.z) },
            )
            val recipientIds = room.humanPlayerIds.filterTo(mutableSetOf()) { server.playerManager.getPlayer(it.toJavaUuid()) != null }
            deliver(server, sync.update(tableId, payload, recipientIds))
        }
    }

    /**
     * 把標示送給仍在線的收件玩家。
     *
     * @param server 目前的伺服器。
     * @param deliveries 要送出的標示。
     */
    private fun deliver(server: MinecraftServer, deliveries: List<TableSpaceObstructionDelivery>) {
        deliveries.forEach { delivery ->
            val player = server.playerManager.getPlayer(delivery.recipientId.toJavaUuid()) ?: return@forEach
            MahjongChannels.tableSpaceObstruction.sendTo(player, json, delivery.payload)
        }
    }

    /**
     * 依桌子位置查詢世界。
     *
     * @param server 目前的伺服器。
     * @param location 桌子位置。
     * @return 桌子所在世界；維度不存在時為 null。
     */
    private fun resolveWorld(server: MinecraftServer, location: TableLocation): ServerWorld? {
        val id = Identifier.tryParse(location.dimensionId) ?: return null
        return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id))
    }

    /** 檢查頻率。 */
    private companion object {
        /** 兩次檢查之間的 tick 數。 */
        const val CHECK_INTERVAL_TICKS: Long = 10L
    }
}
