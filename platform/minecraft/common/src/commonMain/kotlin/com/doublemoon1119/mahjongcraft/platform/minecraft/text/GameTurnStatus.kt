package com.doublemoon1119.mahjongcraft.platform.minecraft.text

/**
 * 玩家目前在對局中的行動狀態，供 [MinecraftPlayerFeedback.ShowHand] 決定手牌畫面該顯示哪種提示文字，
 * 避免玩家在非自己回合時誤以為可以直接 `discard`。
 */
enum class GameTurnStatus {
    /** 輪到自己回合、已經摸牌，可以打牌或宣告特殊動作。 */
    OWN_TURN,

    /** 有資格回應他家的捨牌或搶槓，尚未回應。 */
    AWAITING_RESPONSE,

    /** 都不是——不是自己的回合，也沒有資格回應，純粹等待。 */
    WAITING,
}
