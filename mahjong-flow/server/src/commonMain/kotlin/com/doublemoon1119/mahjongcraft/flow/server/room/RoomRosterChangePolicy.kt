package com.doublemoon1119.mahjongcraft.flow.server.room

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room

/** 不讀取外部狀態的名冊變更檢查；呼叫時可能持有權威鎖。 */
fun interface RoomRosterChangePolicy {
    /**
     * 確認名冊變更符合接入端的額外規則。
     *
     * @param before 變更前的完整房間。
     * @param after 擬提交的完整房間。
     * @return 名冊變更符合額外規則時為 true。
     */
    fun allows(before: Room, after: Room): Boolean

    /** 沒有額外位置限制的預設政策。 */
    companion object {
        /** 純流程環境不另外限制位置。 */
        val UNRESTRICTED: RoomRosterChangePolicy = RoomRosterChangePolicy { _, _ -> true }
    }
}
