package com.doublemoon1119.mahjongcraft.flow.common.game.event

import kotlin.uuid.Uuid

/** 回報規則公開事件標記解析失敗的邊界；失敗不應直接中止事件投影。 */
interface GameEventProjectionReporter {
    /**
     * 回報單一事件的規則標記解析失敗。
     *
     * @param ruleModuleId 標記提供者所屬規則。
     * @param matchId 來源場次。
     * @param eventKind 失敗的投影種類。
     * @param winnerId 和牌標記對應的贏家；其他種類為 null。
     * @param cause 解析或驗證失敗原因。
     */
    fun markerFailed(
        ruleModuleId: String,
        matchId: Uuid,
        eventKind: String,
        winnerId: Uuid?,
        cause: Throwable,
    )

    /** 提供不需要診斷輸出的預設實作。 */
    companion object {
        /** 不記錄解析失敗的預設回報器。 */
        val NONE: GameEventProjectionReporter = object : GameEventProjectionReporter {
            override fun markerFailed(
                ruleModuleId: String,
                matchId: Uuid,
                eventKind: String,
                winnerId: Uuid?,
                cause: Throwable,
            ) = Unit
        }
    }
}
