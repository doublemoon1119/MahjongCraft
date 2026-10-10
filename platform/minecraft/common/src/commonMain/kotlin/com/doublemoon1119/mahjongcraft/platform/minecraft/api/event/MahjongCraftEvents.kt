package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

/**
 * MahjongCraft 對外公開的遊戲事件訂閱入口。
 *
 * 開發環境中使用 debug 指令排除的場次不再發送後續通知，因此訂閱者可能收到對局開始，
 * 但收不到該場之後的結算與對局結束；不得假設每個開始通知必然配對結束通知。
 * 只通知有效伺服器事件 session 內提交的事實；啟動前已完成的清理不補發。
 */
object MahjongCraftEvents {
    /** 對局開始事件訂閱點。 */
    @JvmField
    val MATCH_STARTED: MahjongCraftEvent<MatchStartedListener> = MahjongCraftEvent.create()

    /** 一局結算事件訂閱點。 */
    @JvmField
    val ROUND_SETTLED: MahjongCraftEvent<RoundSettledListener> = MahjongCraftEvent.create()

    /**
     * 對局結束事件訂閱點；包含正常完成與中途終止。
     *
     * Minecraft 清理造成的中途終止原因見 [MinecraftMatchAbortReasonIds]；分數為移除前的當下分數，不重新結算。
     */
    @JvmField
    val MATCH_ENDED: MahjongCraftEvent<MatchEndedListener> = MahjongCraftEvent.create()
}
