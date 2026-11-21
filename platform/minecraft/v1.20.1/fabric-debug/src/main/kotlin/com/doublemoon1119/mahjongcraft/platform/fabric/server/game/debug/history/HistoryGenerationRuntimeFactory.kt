package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history

import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryMatchRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario

/** 為生成工作建立一次性的隔離 Flow 環境，不使用正式玩家集合。 */
fun interface HistoryGenerationRuntimeFactory {
    /**
     * 建立採用指定規則與場長的全 AI 對局。
     *
     * @param scenario 場長情境。
     * @return 已走正式開局流程的隔離環境。
     */
    suspend fun create(scenario: HeadlessHistoryScenario): HeadlessHistoryMatchRuntime
}
