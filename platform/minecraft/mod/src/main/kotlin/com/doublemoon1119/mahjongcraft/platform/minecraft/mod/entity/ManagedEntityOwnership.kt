package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.MahjongDataStatus
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

/**
 * 由對局或等待室管理的 entity 判斷自己的擁有者是否還在。
 *
 * - 擁有者還在時，由明確的清除呼叫移除不再需要的 entity，例如對局結束回到房間時。
 * - 清除當下區塊沒有載入時，entity 會留在存檔裡。entity 存在後的第一個伺服器 tick 依這裡的結果移除自己，世界重新載入、
 *   崩潰重啟後也一樣。
 * - 只看擁有者是否存在，不以存在多久判斷：對局可以進行很久，也可以因所有真人離線而一直暫停。
 * - 讀取 [AuthoritativeStateStore.state] 已發布的快照，不在主執行緒等待權威狀態的鎖。
 * - 麻將資料無法讀取時權威狀態是空的，任何擁有者都會被誤判為不存在，因此一律保留。
 */
object ManagedEntityOwnership {
    /**
     * 判斷 [tableId] 這張桌子的對局是否已不存在。
     *
     * @param tableId entity 所屬的桌子。未指派時為 null。
     * @return 應移除 entity 時為 true。
     */
    fun isGameGone(tableId: Uuid?): Boolean = isGone(tableId) { it.games.keys }

    /**
     * 判斷 [tableId] 這張桌子的等待室是否已不存在。
     *
     * @param tableId entity 所屬的桌子。未指派時為 null。
     * @return 應移除 entity 時為 true。
     */
    fun isRoomGone(tableId: Uuid?): Boolean = isGone(tableId) { it.rooms.keys }

    /**
     * 判斷擁有者是否已不存在。
     *
     * @param ownerId entity 所屬的擁有者。未指派時為 null，視為不存在。
     * @param isDataAvailable 麻將資料是否可以讀取。不能讀取時一律保留。
     * @param ownerIds 目前存在的擁有者。
     * @return 應移除 entity 時為 true。
     */
    internal fun isGone(ownerId: Uuid?, isDataAvailable: Boolean, ownerIds: Set<Uuid>): Boolean = isDataAvailable && (ownerId == null || ownerId !in ownerIds)

    /**
     * 從目前的權威狀態判斷擁有者是否已不存在。
     *
     * @param ownerId entity 所屬的擁有者。
     * @param ownerIds 從權威狀態取出目前存在的擁有者。
     * @return 應移除 entity 時為 true。
     */
    private fun isGone(ownerId: Uuid?, ownerIds: (AuthoritativeStateSnapshot) -> Set<Uuid>): Boolean {
        val koin = GlobalContext.get()
        return isGone(
            ownerId = ownerId,
            isDataAvailable = koin.get<MahjongDataStatus>().isMahjongAvailable,
            ownerIds = ownerIds(koin.get<AuthoritativeStateStore>().state.value),
        )
    }
}
