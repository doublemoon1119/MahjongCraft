package com.doublemoon1119.mahjongcraft.flow.common.game.model

/** 外部玩家是否可以旁觀進行中的遊戲。 */
enum class SpectatingPolicy {
    /** 不接受外部玩家取得遊戲快照。 */
    DISABLED,

    /** 接受外部玩家取得依可見性政策過濾的遊戲快照。 */
    ENABLED,
}
