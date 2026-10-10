package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import kotlinx.coroutines.flow.flow
import kotlin.uuid.Uuid

/** 真實 Flow 的隔離執行環境，每次只推進一個自動操作步驟。 */
interface HeadlessHistoryMatchRuntime {
    /** 場長情境。 */
    val scenario: HeadlessHistoryScenario

    /** 隔離的權威來源儲存。 */
    val store: AuthoritativeStateStore

    /** 來源場地識別碼。 */
    val venueId: Uuid

    /**
     * 取得初始化時固定的整場識別碼。
     *
     * @return 整場識別碼。
     */
    suspend fun matchId(): Uuid

    /**
     * 推進一次正式自動流程。
     *
     * @return 是否造成權威進展。
     */
    suspend fun step(): Boolean

    /**
     * 讀取來源目前對局。
     *
     * @return 目前對局，或已返回房間的 null。
     */
    suspend fun currentGame(): Game?
}
