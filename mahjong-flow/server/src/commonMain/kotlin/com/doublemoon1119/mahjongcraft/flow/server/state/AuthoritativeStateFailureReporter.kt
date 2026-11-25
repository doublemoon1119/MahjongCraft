package com.doublemoon1119.mahjongcraft.flow.server.state

import kotlin.uuid.Uuid

/**
 * 接收權威狀態交易中已隔離、但不能無聲略過的失敗；交易本身照常提交。
 */
interface AuthoritativeStateFailureReporter {
    /**
     * 歷史事件無法產生或寫入待寫佇列，這一場的牌譜從這裡起留下序號缺口。
     *
     * @param matchId 牌譜缺資料的場次。
     * @param failure 產生或寫入事件時丟出的失敗。
     */
    fun onHistoryRecordingFailed(matchId: Uuid, failure: Throwable)

    /**
     * 牌桌變化無法記成差異，這一步改存完整牌桌。
     *
     * @param matchId 改存完整牌桌的場次。
     * @param failure 計算差異時丟出的失敗。
     */
    fun onHistoryTableChangeUnavailable(matchId: Uuid, failure: Throwable)

    /**
     * 提交後通知接收者時，接收者丟出失敗；其他場地與之後的通知照常進行。
     *
     * @param failure 接收者丟出的失敗。
     */
    fun onCommittedFactsListenerFailed(failure: Throwable)

    /** [NONE] 所在的伴生物件。 */
    companion object {
        /** 不記錄任何失敗的接收者。 */
        val NONE: AuthoritativeStateFailureReporter = object : AuthoritativeStateFailureReporter {
            override fun onHistoryRecordingFailed(matchId: Uuid, failure: Throwable) = Unit

            override fun onHistoryTableChangeUnavailable(matchId: Uuid, failure: Throwable) = Unit

            override fun onCommittedFactsListenerFailed(failure: Throwable) = Unit
        }
    }
}
