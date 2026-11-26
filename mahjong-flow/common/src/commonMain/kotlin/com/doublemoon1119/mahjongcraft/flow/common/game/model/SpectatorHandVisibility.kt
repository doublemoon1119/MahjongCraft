package com.doublemoon1119.mahjongcraft.flow.common.game.model

/** 外部旁觀者取得遊戲快照時的手牌可見範圍。 */
enum class SpectatorHandVisibility {
    /** 隱藏所有玩家的未公開手牌。 */
    HIDDEN,

    /** 顯示所有玩家的手牌。 */
    REVEALED,
}
