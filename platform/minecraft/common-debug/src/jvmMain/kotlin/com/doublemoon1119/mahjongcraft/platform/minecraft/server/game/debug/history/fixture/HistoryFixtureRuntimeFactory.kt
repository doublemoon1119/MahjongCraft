package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

/** 為測試牌譜建立一次性的隔離對局，不使用正式玩家集合。 */
fun interface HistoryFixtureRuntimeFactory {
    /**
     * 建立照腳本進行的測試牌譜對局。
     *
     * @param fixture 測試牌譜。
     * @return 已走正式開局流程、可檢查腳本是否照順序執行完的對局環境。
     */
    suspend fun createFixture(fixture: HistoryFixture): HistoryFixtureRuntime
}
