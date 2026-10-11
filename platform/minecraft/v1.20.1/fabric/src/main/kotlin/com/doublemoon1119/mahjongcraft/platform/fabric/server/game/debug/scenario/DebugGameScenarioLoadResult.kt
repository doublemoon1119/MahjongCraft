package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

/** 載入 development-only 權威情境時可能回傳的結果。 */
sealed interface DebugGameScenarioLoadResult {
    /**
     * 情境已成功寫入並完成同步。
     *
     * @property scenarioId 已載入的 scenario ID。
     * @property presentationCount 本次完整重建桌面呈現的次數。
     */
    data class Success(
        val scenarioId: String,
        val presentationCount: Int = 1,
    ) : DebugGameScenarioLoadResult

    /**
     * 情境載入遭拒，並攜帶供 debug 指令顯示的原因。
     *
     * @property reason 不含玩家資料的拒絕原因。
     */
    data class Rejected(
        val reason: String,
    ) : DebugGameScenarioLoadResult
}
