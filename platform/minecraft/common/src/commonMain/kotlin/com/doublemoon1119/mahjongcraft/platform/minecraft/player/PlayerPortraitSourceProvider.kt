package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/** 只解析宣告式來源；座位 actor 的生成、姿態、存檔與清理未來由獨立 SeatActorProvider 負責。 */
fun interface PlayerPortraitSourceProvider {
    /**
     * 解析指定玩家的 portrait 來源。
     *
     * @param context 規則中立的玩家身分。
     * @return 可用的 portrait 來源；沒有來源時為 null。
     */
    fun resolve(context: PlayerPortraitSourceContext): PlayerPortraitSource?
}
