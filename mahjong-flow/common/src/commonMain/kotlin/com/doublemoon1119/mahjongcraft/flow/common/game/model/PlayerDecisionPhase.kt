package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 玩家目前取得決策權的流程階段。
 *
 * 此型別由權威桌況解析，供 timer manager、timeout policy、scheduler 與網路 DTO mapping 共用；
 * 它不代表麻將規則中的動作種類，也不直接作為網路 schema。
 */
enum class PlayerDecisionPhase {
    /** 玩家自己的回合，已完成摸牌或剛完成吃碰而需要選擇動作。 */
    OWN_TURN,

    /** 玩家正在回應其他玩家的捨牌。 */
    DISCARD_REACTION,

    /** 玩家正在回應暗槓或加槓的搶槓視窗。 */
    KAN_REACTION,
}
