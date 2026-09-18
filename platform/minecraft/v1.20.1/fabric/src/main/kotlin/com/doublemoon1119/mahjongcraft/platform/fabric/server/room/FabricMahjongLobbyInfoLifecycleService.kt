package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.DimensionChunkKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 依權威 Room 狀態維護每桌的等待中遊戲提示。
 *
 * 兩個觸發來源：權威狀態變更時更新內容有變的那幾桌；區塊載入時補上該區塊內桌子的提示——提示是世界
 * 中的 entity，區塊沒載入時無法建立，因此不能只靠狀態變更。兩者都在伺服器主執行緒執行。
 *
 * @property store 房間與對局的權威狀態。
 * @property scope 隨 server session 建立與取消的協程作用域。
 * @property dispatchers 提供伺服器主執行緒的調度器。
 * @property locations 麻將桌位置索引。
 * @property presenter 實際建立、更新與清除提示的呈現器。
 */
@Single
class FabricMahjongLobbyInfoLifecycleService(
    private val store: AuthoritativeStateStore,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val locations: TableLocationRegistry,
    private val presenter: FabricMahjongLobbyInfoPresenter,
) {
    /** 上一次處理過的房間，用來只更新內容真的有變的桌子。 */
    private var lastRoomsById: Map<Uuid, Room> = emptyMap()

    /** 註冊區塊載入時的補畫。 */
    fun registerEvents() {
        ServerChunkEvents.CHUNK_LOAD.register { world, chunk ->
            val key = DimensionChunkKey(
                dimensionId = world.registryKey.value.toString(),
                chunkX = chunk.pos.x,
                chunkZ = chunk.pos.z,
            )
            val tableIds = locations.getByChunk(key).map { it.tableId }
            if (tableIds.isEmpty()) return@register
            scope.launch(dispatchers.main) {
                val rooms = store.state.value.rooms
                tableIds.forEach { tableId -> refresh(tableId, rooms[tableId]) }
            }
        }
    }

    /** 開始追蹤權威狀態；協程隨 server session 的作用域一起結束。 */
    fun startSession() {
        scope.launch(dispatchers.main) {
            store.state.collect { state -> onState(state.rooms) }
        }
    }

    /** 清除上一次的比較基準，讓下一個 session 重新建立所有提示。 */
    fun stopSession() {
        lastRoomsById = emptyMap()
    }

    /** 只更新與上次相比有變化的桌子；房間消失的桌子清除提示。 */
    private fun onState(rooms: Map<Uuid, Room>) {
        val changedTableIds = (rooms.keys + lastRoomsById.keys).filter { rooms[it] !== lastRoomsById[it] }
        lastRoomsById = rooms
        changedTableIds.forEach { tableId -> refresh(tableId, rooms[tableId]) }
    }

    /** 依該桌目前是否有房間，建立／更新或清除提示；區塊未載入時呈現器自行忽略。 */
    private fun refresh(tableId: Uuid, room: Room?) {
        if (room == null) {
            presenter.clear(tableId)
        } else {
            presenter.present(tableId, room.gameConfig, room.playerIds.size)
        }
    }
}
