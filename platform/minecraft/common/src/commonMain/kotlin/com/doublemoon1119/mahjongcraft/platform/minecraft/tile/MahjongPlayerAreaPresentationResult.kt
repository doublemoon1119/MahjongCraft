package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 正式桌角區域呈現請求的處理結果。 */
enum class MahjongPlayerAreaPresentationResult {
    /** 已把這位玩家的手牌／摸牌位／副露更新為本次要呈現的狀態。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /**
     * 其中一張以上的牌找不到對應的既有 entity 可以領走——手牌／副露的牌應該早在牌牆生成時就已經
     * 存在（同一個 UUID），這裡不重新建立新 entity，只做「找到、改標記、移動」；找不到通常代表遊戲
     * 狀態跟世界狀態已經不一致。找不到的牌會被跳過，其餘牌仍照常呈現，不是整批回滾。
     */
    SPAWN_FAILED,
}
