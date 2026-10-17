package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import kotlin.uuid.Uuid

/** 權威中止完成後，由執行環境清理呈現、通知參與者並記錄診斷。 */
fun interface GameAbortEffects {
    /**
     * 處理已中止的場次；不得再修改權威對局，也不得取消正在收尾的呼叫端。
     *
     * 實作須隔離各項清理失敗，不得因通知失敗跳過其餘清理；取消仍依執行階段生命週期處理。
     * 排程後的工作須確認仍是同一次啟動與回房狀態，不能清掉後來的新場次。
     *
     * @param game 被移除的對局快照。
     * @param room 與中止同筆交易建立的房間，用於拒絕過期清理。
     * @param observerIds 中止時已知的參與者與觀察者。
     * @param reasonId 可公開的終止原因識別碼。
     * @param diagnostic 僅供管理診斷的說明。
     */
    suspend fun apply(
        game: Game,
        room: Room,
        observerIds: Set<Uuid>,
        reasonId: String,
        diagnostic: String,
    )

    /** 不需要外部呈現的執行環境可明確使用此空實作。 */
    companion object {
        /** 不做外部副作用，權威中止仍由用例執行。 */
        val NONE = GameAbortEffects { _, _, _, _, _ -> }
    }
}
