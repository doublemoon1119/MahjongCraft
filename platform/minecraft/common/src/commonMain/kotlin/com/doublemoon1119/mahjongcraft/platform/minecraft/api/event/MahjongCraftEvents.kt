package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

/** MahjongCraft 對外公開的遊戲事件訂閱入口。 */
object MahjongCraftEvents {
    /** 對局開始事件訂閱點。 */
    @JvmField
    val MATCH_STARTED: MahjongCraftEvent<MatchStartedListener> = MahjongCraftEvent.create()

    /** 一局結算事件訂閱點。 */
    @JvmField
    val ROUND_SETTLED: MahjongCraftEvent<RoundSettledListener> = MahjongCraftEvent.create()

    /** 對局結束事件訂閱點。 */
    @JvmField
    val MATCH_ENDED: MahjongCraftEvent<MatchEndedListener> = MahjongCraftEvent.create()
}
