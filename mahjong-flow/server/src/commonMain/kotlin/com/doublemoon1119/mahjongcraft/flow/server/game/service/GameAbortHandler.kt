package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game

/** 對無法安全繼續的場次執行一次受條件保護的中止與回房。 */
fun interface GameAbortHandler {
    /**
     * 只中止仍符合失敗時權威快照的場次，不補造正常終局或修改分數。
     *
     * @param expectedGame 發現錯誤時的權威對局。
     * @param reasonId 可公開的終止原因識別碼。
     * @param diagnostic 僅提供給管理診斷的說明。
     * @return 本次確實提交中止時為 true；過期或已結束時為 false。
     */
    suspend fun abort(expectedGame: Game, reasonId: String, diagnostic: String): Boolean
}
