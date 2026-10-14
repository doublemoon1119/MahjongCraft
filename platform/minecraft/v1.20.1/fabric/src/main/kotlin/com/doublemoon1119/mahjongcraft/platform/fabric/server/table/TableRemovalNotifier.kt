package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import kotlin.uuid.Uuid

/** 捕捉桌子移除通知的來源，並在權威清理完成後安排不阻塞主執行緒的交付。 */
interface TableRemovalNotifier {
    /**
     * 取得仍存在的原桌子與追蹤者；沒有有效 session 時不建立通知。
     *
     * @param tableId 要清理的桌子。
     * @return 可以安排通知的原來源；未啟動 session 時為 null。
     */
    suspend fun capture(tableId: Uuid): TableRemovalNotification?

    /**
     * 確認捕捉的來源未被取代。
     *
     * @param notification 清理前捕捉的來源。
     * @return 原 session 與條目仍有效時為 true。
     */
    fun isCurrent(notification: TableRemovalNotification): Boolean

    /**
     * 登記成功清理，阻止原來源再交付可重建桌況的回覆。
     *
     * @param notification 已確認完成權威移除的來源。
     * @return 原來源仍相符並已登記時為 true；被取代時不得繼續清理。
     */
    fun beginRemoval(notification: TableRemovalNotification): Boolean

    /**
     * 索引移除後安排交付，不等待送出工作完成。
     *
     * @param notification 清理前捕捉的來源。
     * @param additionalObserverIds 參與者及已保存觀察者的接收者快照。
     */
    fun completeRemoval(notification: TableRemovalNotification, additionalObserverIds: Set<Uuid>)

    /**
     * 清理後續工作失敗或取消時，釋放本次移除標記，不回復已提交的權威狀態。
     *
     * @param notification 需要釋放的原來源。
     */
    fun cancelRemoval(notification: TableRemovalNotification)
}
