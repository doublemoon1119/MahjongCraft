package com.doublemoon1119.mahjongcraft.logic.config

/**
 * 定義遊戲的積分與結算配置介面。
 * * 此介面描述了對局開始的初始狀態以及結束判定門檻。
 */
interface ScoreConfig {
    /** 遊戲開始時每位玩家的初始分數。 */
    val initialScore: Int

    /** 判定遊戲結束的分數門檻（例如擊飛機制）。
     * 若玩家分數低於此值，對局可能提前結束。若為 null 則代表無此機制。
     */
    val bustThreshold: Int?
}
