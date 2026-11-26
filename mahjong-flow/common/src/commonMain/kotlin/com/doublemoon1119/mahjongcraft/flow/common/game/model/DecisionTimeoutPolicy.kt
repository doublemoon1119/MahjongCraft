package com.doublemoon1119.mahjongcraft.flow.common.game.model

/** 玩家耗盡基本思考時間與保留思考時間後採用的流程政策。 */
enum class DecisionTimeoutPolicy {
    /** 停止接受玩家手動操作，並由伺服器自動摸切及跳過所有反應至遊戲結束。 */
    FORCED_AUTO_PLAY,
}
