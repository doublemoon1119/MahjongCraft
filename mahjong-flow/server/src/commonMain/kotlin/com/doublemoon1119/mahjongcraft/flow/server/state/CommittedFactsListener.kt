package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts

/**
 * 接收已完成權威交易的事實通知。
 *
 * 提交通知在權威交易鎖內依提交順序發出；接收者不得阻塞、重入 store 或把通知視為持久化保證。
 * 交易釋放通知則在交易鎖釋放後發出。
 */
interface CommittedFactsListener {
    /** 常駐但不接收通知的 listener。 */
    companion object {
        /** 不處理任何提交或釋放通知的共用接收者。 */
        val NONE: CommittedFactsListener = object : CommittedFactsListener {
            override fun onCommitted(sequence: Long, facts: CommittedGameFacts) = Unit

            override fun onReleased(sequence: Long) = Unit
        }
    }

    /**
     * 接收交易中某一場地的已提交事實。此方法在 store 交易鎖內執行，實作不得阻塞或重入 store。
     *
     * 同一筆交易的所有 [facts] 會使用相同的 [sequence]；不同交易的序號在 store 生命週期內遞增。
     *
     * @param sequence 這筆權威交易的單調遞增序號。
     * @param facts 這筆交易對單一場地提交的事實。
     */
    fun onCommitted(sequence: Long, facts: CommittedGameFacts)

    /**
     * 通知一筆交易的所有事實通知都已嘗試交付。
     *
     * 此方法在交易互斥鎖釋放後呼叫；接收者可以在此讀取 store。即使個別 [onCommitted] 失敗，也會呼叫。
     *
     * @param sequence 已完成交付嘗試的權威交易序號。
     */
    fun onReleased(sequence: Long)
}
